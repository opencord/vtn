import os
import requests
import socket
import sys
import base64
from django.db.models import F, Q
from xos.config import Config
from synchronizers.base.syncstep import SyncStep
from core.models import Service, Network, Port
from core.models.service import COARSE_KIND
from services.vtn.vtnnetport import VTNNetwork, VTNPort
from services.vtn.models import VTNService
from xos.logger import Logger, logging
from requests.auth import HTTPBasicAuth

# hpclibrary will be in steps/..
parentdir = os.path.join(os.path.dirname(__file__),"..")
sys.path.insert(0,parentdir)

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
        from services.onos.models import ONOSService

        vtn_service = ONOSService.get_service_objects().filter(name="ONOS_CORD")  # XXX fixme - harcoded
        if not vtn_service:
            raise "No VTN Onos Service"

        return vtn_service[0]

    def get_vtn_auth(self):
        return HTTPBasicAuth('karaf', 'karaf') # XXX fixme - hardcoded auth

    def get_vtn_addr(self):
        vtn_service = self.get_vtn_onos_service()

        if vtn_service.rest_hostname:
            return vtn_service.rest_hostname

        if not vtn_service.slices.exists():
            raise "VTN Service has no slices"

        vtn_slice = vtn_service.slices.all()[0]

        if not vtn_slice.instances.exists():
            raise "VTN Slice has no instances"

        vtn_instance = vtn_slice.instances.all()[0]

        return vtn_instance.node.name

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
                url = "http://" + self.get_vtn_addr() + ":8181/onos/cordvtn/service-dependency/%s/%s" % (vtn_map[0], vtn_map[1])

                print "POST %s" % url
                r = requests.post(url, auth=self.get_vtn_auth() )
                if (r.status_code != 200):
                    raise Exception("Received error from vtn service (%d)" % r.status_code)

        for vtn_map in glo_saved_vtn_maps:
            if not vtn_map in vtn_maps:
                # call vtn rest api to delete map
                url = "http://" + self.get_vtn_addr() + ":8181/onos/cordvtn/service-dependency/%s/%s" % (vtn_map[0],vtn_map[1])

                print "DELETE %s" % url
                r = requests.delete(url, auth=self.get_vtn_auth() )
                if (r.status_code != 200):
                    raise Exception("Received error from vtn service (%d)" % r.status_code)

        glo_saved_vtn_maps = vtn_maps
        # TODO: save this

    def sync_service_networks(self):
        valid_ids = []
        for network in Network.objects.all():
            network = VTNNetwork(network)
            valid_ids.append(network.id)
            if (glo_saved_networks.get(network.id, None) != network.to_dict()):
                logger.info("POSTing VTN API for network %s" % network.id)

                url = "http://" + self.get_vtn_addr() + ":8181/onos/serviceNetworks/%s" % network.id
                logger.info("URL: %s" % url)

                data = {"id": network.id,
                        "type": network.type,
                        "providerNetworks": network.providerNetworks}
                logger.info("DATA: %s" % str(data))

                r=requests.post(url, data=data, auth=self.get_vtn_auth() )
                if (r.status_code in [200]):
                    glo_saved_networks[network.id] = network.to_dict()
                else:
                    logger.error("Received error from vtn service (%d)" % r.status_code)

        for network_id in glo_saved_networks.keys():
            if network_id not in valid_ids:
                logger.info("DELETEing VTN API for network %s" % network_id)

                url = "http://" + self.get_vtn_addr() + ":8181/onos/serviceNetworks/%s" % network_id
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
            valid_ids.append(port.id)
            if (glo_saved_ports.get(port.id, None) != port.to_dict()):
                logger.info("POSTing VTN API for port %s" % port.id)

                url = "http://" + self.get_vtn_addr() + ":8181/onos/servicePorts/%s" % port.id
                logger.info("URL: %s" % url)

                data = {"id": port.id,
                        "vlan_id": port.vlan_id,
                        "floating_address_pairs": port.floating_address_pairs}
                logger.info("DATA: %s" % str(data))

                r=requests.post(url, data=data, auth=self.get_vtn_auth() )
                if (r.status_code in [200]):
                    glo_saved_ports[port.id] = port.to_dict()
                else:
                    logger.error("Received error from vtn service (%d)" % r.status_code)

        for port_id in glo_saved_ports.keys():
            if port_id not in valid_ids:
                logger.info("DELETEing VTN API for port %s" % port_id)

                url = "http://" + self.get_vtn_addr() + ":8181/onos/servicePorts/%s" % port_id
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

        if vtn_service.vtnAPIVersion==2:
            # version 2 means use new API
            logger.info("Using New API")
            self.sync_service_networks()
            self.sync_service_ports()
        else:
            # default to legacy
            logger.info("Using Old API")
            self.sync_legacy_vtn_api()


