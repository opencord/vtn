import os
import requests
import socket
import sys
import base64
from xos.config import Config
from services.vtn.vtnnetport import VTNNetwork, VTNPort
from synchronizers.new_base.syncstep import SyncStep
from synchronizers.new_base.modelaccessor import *
from xos.logger import Logger, logging
from requests.auth import HTTPBasicAuth

logger = Logger(level=logging.INFO)

# XXX should save and load this
glo_saved_vtn_maps = []
glo_saved_networks = {}
glo_saved_ports = {}

class SyncVTNService(SyncStep):
    provides=[Service]
    observes=Service
    requested_interval=0

    def __init__(self, **args):
        SyncStep.__init__(self, **args)

    def get_vtn_onos_service(self):
        vtn_service = ONOSService.objects.filter(name="ONOS_CORD")  # XXX fixme - harcoded
        if not vtn_service:
            raise "No VTN Onos Service"

        return vtn_service[0]

    def get_vtn_auth(self):
        return HTTPBasicAuth('karaf', 'karaf') # XXX fixme - hardcoded auth

    def get_vtn_addr(self):
        vtn_service = self.get_vtn_onos_service()

        if vtn_service.rest_hostname:
            return vtn_service.rest_hostname

        # code below this point is for ONOS running in a slice, and is
        # probably outdated

        if not vtn_service.slices.exists():
            raise "VTN Service has no slices"

        vtn_slice = vtn_service.slices.all()[0]

        if not vtn_slice.instances.exists():
            raise "VTN Slice has no instances"

        vtn_instance = vtn_slice.instances.all()[0]

        return vtn_instance.node.name

    def get_vtn_port(self):
        vtn_service = self.get_vtn_onos_service()

        if vtn_service.rest_port:
            return vtn_service.rest_port

        # code below this point is for ONOS running in a slice, and is
        # probably outdated

        raise Exception("Must set rest_port")

    def sync_legacy_vtn_api(self):
        global glo_saved_vtn_maps

        logger.info("sync'ing vtn services")

        vtn_maps = []
        for service in Service.objects.all():
           for id in service.get_vtn_src_ids():
               dependencies = service.get_vtn_dependencies_ids()
               if dependencies:
                   for dependency in dependencies:
                       vtn_maps.append( (id, dependency) )

        for vtn_map in vtn_maps:
            if not (vtn_map in glo_saved_vtn_maps):
                # call vtn rest api to add map
                url = "http://" + self.get_vtn_addr() + ":" + str(self.get_vtn_port()) + "/onos/cordvtn/service-dependency/%s/%s" % (vtn_map[0], vtn_map[1])

                print "POST %s" % url
                r = requests.post(url, auth=self.get_vtn_auth() )
                if (r.status_code != 200):
                    raise Exception("Received error from vtn service (%d)" % r.status_code)

        for vtn_map in glo_saved_vtn_maps:
            if not vtn_map in vtn_maps:
                # call vtn rest api to delete map
                url = "http://" + self.get_vtn_addr() +  ":" + str(self.get_vtn_port()) + "/onos/cordvtn/service-dependency/%s/%s" % (vtn_map[0],vtn_map[1])

                print "DELETE %s" % url
                r = requests.delete(url, auth=self.get_vtn_auth() )
                if (r.status_code != 200):
                    raise Exception("Received error from vtn service (%d)" % r.status_code)

        glo_saved_vtn_maps = vtn_maps
        # TODO: save this

    def get_method(self, url, id):
        url_with_id = "%s/%s" % (url, id)
        r = requests.get(url_with_id, auth=self.get_vtn_auth())
        if (r.status_code==200):
            method="PUT"
            url = url_with_id
            req_func = requests.put
            exists=True
        else:
            method="POST"
            req_func = requests.post
            exists=False
        return (exists, url, method, req_func)

    def sync_service_networks(self):
        valid_ids = []
        for network in Network.objects.all():
            network = VTNNetwork(network)

            if not network.id:
                continue

            if (network.type=="PRIVATE") and (not network.providerNetworks):
                logger.info("Skipping network %s because it has no relevant state" % network.id)
                continue

            valid_ids.append(network.id)

            if (glo_saved_networks.get(network.id, None) != network.to_dict()):
                (exists, url, method, req_func) = self.get_method("http://" + self.get_vtn_addr() +  ":" + str(self.get_vtn_port()) + "/onos/cordvtn/serviceNetworks", network.id)

                logger.info("%sing VTN API for network %s" % (method, network.id))

                logger.info("URL: %s" % url)

                # clean the providerNetworks list
                providerNetworks = [{"id": x["id"], "bidirectional": x["bidirectional"]} for x in network.providerNetworks]

                data = {"ServiceNetwork": {"id": network.id,
                        "type": network.type,
                        "providerNetworks": providerNetworks} }
                logger.info("DATA: %s" % str(data))

                r=req_func(url, json=data, auth=self.get_vtn_auth() )
                if (r.status_code in [200,201]):
                    glo_saved_networks[network.id] = network.to_dict()
                else:
                    logger.error("Received error from vtn service (%d)" % r.status_code)


        for network_id in glo_saved_networks.keys():
            if network_id not in valid_ids:
                logger.info("DELETEing VTN API for network %s" % network_id)

                url = "http://" + self.get_vtn_addr() +  ":" + str(self.get_vtn_port()) + "/onos/cordvtn/serviceNetworks/%s" % network_id
                logger.info("URL: %s" % url)

                r = requests.delete(url, auth=self.get_vtn_auth() )
                if (r.status_code in [200,204]):
                    del glo_saved_networks[network_id]
                else:
                    logger.error("Received error from vtn service (%d)" % r.status_code)

    def sync_service_ports(self):
        valid_ids = []
        for port in Port.objects.all():
            port = VTNPort(port)

            if not port.id:
                continue

            if (not port.vlan_id) and (not port.floating_address_pairs):
                logger.info("Skipping port %s because it has no relevant state" % port.id)
                continue

            valid_ids.append(port.id)

            if (glo_saved_ports.get(port.id, None) != port.to_dict()):
                (exists, url, method, req_func) = self.get_method("http://" + self.get_vtn_addr() +  ":" + str(self.get_vtn_port()) + "/onos/cordvtn/servicePorts", port.id)

                logger.info("%sing VTN API for port %s" % (method, port.id))

                logger.info("URL: %s" % url)

                data = {"ServicePort": {"id": port.id,
                        "vlan_id": port.vlan_id,
                        "floating_address_pairs": port.floating_address_pairs} }
                logger.info("DATA: %s" % str(data))

                r=req_func(url, json=data, auth=self.get_vtn_auth() )
                if (r.status_code in [200,201]):
                    glo_saved_ports[port.id] = port.to_dict()
                else:
                    logger.error("Received error from vtn service (%d)" % r.status_code)

        for port_id in glo_saved_ports.keys():
            if port_id not in valid_ids:
                logger.info("DELETEing VTN API for port %s" % port_id)

                url = "http://" + self.get_vtn_addr() +  ":" + str(self.get_vtn_port()) + "/onos/cordvtn/servicePorts/%s" % port_id
                logger.info("URL: %s" % url)

                r = requests.delete(url, auth=self.get_vtn_auth() )
                if (r.status_code in [200,204]):
                    del glo_saved_ports[port_id]
                else:
                    logger.error("Received error from vtn service (%d)" % r.status_code)

    def call(self, **args):
        vtn_service = VTNService.get_service_objects().all()
        if not vtn_service:
            raise Exception("No VTN Service")

        vtn_service = vtn_service[0]

        if vtn_service.vtnAPIVersion>=2:
            # version 2 means use new API
            logger.info("Using New API")
            self.sync_service_networks()
            self.sync_service_ports()
        else:
            # default to legacy
            logger.info("Using Old API")
            raise Exception("VTN API Version 1 is no longer supported by VTN Synchronizer")
            #self.sync_legacy_vtn_api()


