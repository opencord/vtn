from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework.reverse import reverse
from rest_framework import serializers
from rest_framework import generics
from rest_framework import viewsets
from rest_framework.decorators import detail_route, list_route
from rest_framework.views import APIView
from rest_framework import status
from core.models import *
from services.vtn.models import VTNService
from django.forms import widgets
from django.conf.urls import patterns, url
from api.xosapi_helpers import PlusModelSerializer, XOSViewSet, ReadOnlyField
from django.shortcuts import get_object_or_404
from xos.apibase import XOSListCreateAPIView, XOSRetrieveUpdateDestroyAPIView, XOSPermissionDenied
from xos.exceptions import *
import json
import subprocess

VTN_SERVCOMP_KINDS=["PRIVATE","VSG"]

class VTNServiceSerializer(PlusModelSerializer):
    id = ReadOnlyField()

    privateGatewayMac = serializers.CharField(required=False)
    localManagementIp = serializers.CharField(required=False)
    ovsdbPort = serializers.IntegerField(required=False)
    sshPort = serializers.IntegerField(required=False)
    sshUser = serializers.CharField(required=False)
    sshKeyFile = serializers.CharField(required=False)
    mgmtSubnetBits = serializers.IntegerField(required=False)
    xosEndpoint = serializers.CharField(required=False)
    xosUser = serializers.CharField(required=False)
    xosPassword = serializers.CharField(required=False)

    humanReadableName = serializers.SerializerMethodField("getHumanReadableName")
    class Meta:
        model = VTNService
        fields = ('humanReadableName', 'id', 'privateGatewayMac', 'localManagementIp', 'ovsdbPort', 'sshPort', 'sshUser', 'sshKeyFile',
                  'mgmtSubnetBits', 'xosEndpoint', 'xosUser', 'xosPassword')

    def getHumanReadableName(self, obj):
        return obj.__unicode__()

class VTNNetwork(object):
    def __init__(self, xos_network=None):
        self.xos_network = xos_network

    def get_controller_network(self):
        for cn in self.xos_network.controllernetworks.all():
            # TODO: find the right one
            return cn
        return None

    def get_cn_field(self, fieldname):
        cn=self.get_controller_network()
        if not cn:
            return None
        return getattr(cn, fieldname)

    @property
    def id(self):
        return self.get_cn_field("net_id")

    @property
    def name(self):
        return self.xos_network.name

    @property
    def subnet(self):
        return self.get_cn_field("subnet")

    @property
    def gateway(self):
        return self.get_cn_field("gateway")

    @property
    def segmentation_id(self):
        return self.get_cn_field("segmentation_id")

    @property
    def type(self):
        return self.xos_network.template.vtn_kind

    @property
    def providerNetworks(self):
        slice = self.xos_network.owner
        service = slice.service
        if not service:
            return []

        nets=[]
        for tenant in service.subscribed_tenants.all():
            if tenant.provider_service:
                bidirectional = tenant.connect_method!="private-unidirectional"
                for slice in tenant.provider_service.slices.all():
                    for net in slice.networks.all():
                        if net.template.vtn_kind not in VTN_SERVCOMP_KINDS:
                            continue

                        if not net.controllernetworks.exists():
                            continue

                        cn = net.controllernetworks.all()[0]
                        nets.append({"id": cn.net_id,
                                     "name": net.name,
                                     "bidirectional": bidirectional})
        return nets

    @property
    def subscriberNetworks(self):
        slice = self.xos_network.owner
        service = slice.service
        if not service:
            return []

        nets=[]
        for tenant in service.provided_tenants.all():
            if tenant.subscriber_service:
                bidirectional = tenant.connect_method!="private-unidirectional"
                for slice in tenant.subscriber_service.slices.all():
                    for net in slice.networks.all():
                        if net.template.vtn_kind not in VTN_SERVCOMP_KINDS:
                            continue

                        if not net.controllernetworks.exists():
                            continue

                        cn = net.controllernetworks.all()[0]
                        nets.append({"id": cn.net_id,
                                     "name": net.name,
                                     "bidirectional": bidirectional})
        return nets

    @property
    def ownerSliceName(self):
        if self.xos_network.owner:
            return self.xos_network.owner.name
        return None

    @property
    def ownerServiceName(self):
        if self.xos_network.owner and self.xos_network.owner.service:
            return self.xos_network.owner.service.name
        return None

class VTNNetworkSerializer(serializers.Serializer):
    id = ReadOnlyField()
    name = serializers.CharField(required=False)
    subnet = serializers.CharField(required=False)
    gateway = serializers.CharField(required=False)
    segmentation_id = serializers.CharField(required=False)

    class Meta:
        fields = ('id', 'name', 'subnet', 'gateway', 'segmentation_id')

class VTNServiceNetworkSerializer(serializers.Serializer):
    id = ReadOnlyField()
    type = serializers.CharField(required=False)
    subscriberNetworks = serializers.SerializerMethodField("getSubscriberNetworks")
    providerNetworks = serializers.SerializerMethodField("getProviderNetworks")
    ownerSliceName = ReadOnlyField()
    ownerServiceName = ReadOnlyField()

    class Meta:
        fields = ('id', 'type', "subscriberNetworks", "providerNetworks", "ownerSliceName", "ownerServiceName")

    def getSubscriberNetworks(self, obj):
         return obj.subscriberNetworks

    def getProviderNetworks(self, obj):
         return obj.providerNetworks

class VTNPort(object):
    def __init__(self, xos_port=None):
        self.xos_port = xos_port

    def get_controller_network(self):
        for cn in self.xos_port.network.controllernetworks.all():
            # TODO: find the right one
            return cn
        return None

    def get_vsg_tenant(self):
        from services.vsg.models import VSGTenant
        for tenant in VSGTenant.get_tenant_objects().all():
            if tenant.instance == self.xos_port.instance:
                return tenant
        return None

    @property
    def vlan_id(self):
        if not self.xos_port.instance:
            return None
        tags = Tag.select_by_content_object(self.xos_port.instance).filter(name="s_tag")
        if not tags:
            return None
        return tags[0].value

    @property
    def floating_address_pairs(self):
        address_pairs = []
        vsg = self.get_vsg_tenant()
        if vsg:
            if vsg.wan_container_ip and vsg.wan_container_mac:
                address_pairs.append({"ip_address": vsg.wan_container_ip,
                                      "mac_address": vsg.wan_container_mac})

            if vsg.wan_vm_ip and vsg.wan_vm_mac:
                address_pairs.append({"ip_address": vsg.wan_vm_ip,
                                      "mac_address": vsg.wan_vm_mac})

        return address_pairs

    @property
    def id(self):
        return self.xos_port.port_id

    @property
    def name(self):
        return "port-%s" % self.xos_port.id

    @property
    def network_id(self):
        cn = self.get_controller_network()
        if not cn:
            return None
        return cn.net_id

    @property
    def mac_address(self):
        return self.xos_port.mac

    @property
    def ip_address(self):
        return self.xos_port.ip

class VTNPortSerializer(serializers.Serializer):
    id = ReadOnlyField()
    name = serializers.CharField(required=False)
    network_id = serializers.CharField(required=False)
    mac_address = serializers.CharField(required=False)
    ip_address = serializers.CharField(required=False)

    class Meta:
        fields = ('id', 'name', 'network_id', 'mac_address', 'ip_address')

class FloatingAddressPairSerializer(serializers.Serializer):
    ip_address = ReadOnlyField()
    mac_address = ReadOnlyField()

class VTNServicePortSerializer(serializers.Serializer):
    id = ReadOnlyField()
    vlan_id = serializers.IntegerField(required=False)

    # TODO: structure this better
    floating_address_pairs = serializers.SerializerMethodField("getFloatingAddressPairs")

    class Meta:
        fields = ('id', 'vlan_id')

    def getFloatingAddressPairs(self, obj):
         return obj.floating_address_pairs

class VTNViewSet(XOSViewSet):
    base_name = "vtn"
    method_name = "vtn"
    method_kind = "viewset"

    # these are just because ViewSet needs some queryset and model, even if we don't use the
    # default endpoints
    queryset = VTNService.get_service_objects().all()
    model = VTNService
    serializer_class = VTNServiceSerializer

    custom_serializers = {"get_port": VTNPortSerializer,
                          "get_ports": VTNPortSerializer,
                          "get_network": VTNNetworkSerializer,
                          "get_networks": VTNNetworkSerializer}

    @classmethod
    def get_urlpatterns(self, api_path="^"):
        patterns = []

        patterns.append( self.detail_url("services/$", {"get": "get_services"}, "services") )
        patterns.append( self.detail_url("services_names/$", {"get": "get_services_names"}, "services") )
        patterns.append( self.detail_url("services/(?P<service>[a-zA-Z0-9\-_]+)/$", {"get": "get_service"}, "get_service") )

        # Not as RESTful as it could be, but maintain these endpoints for compability
        patterns.append( self.list_url("services/$", {"get": "get_services"}, "rootvtn_services") )
        patterns.append( self.list_url("services_names/$", {"get": "get_services_names"}, "rootvtn_services") )
        patterns.append( self.list_url("services/(?P<service>[a-zA-Z0-9\-_]+)/$", {"get": "get_service"}, "rootvtn_get_service") )

        # Neutron-replacement APIs
        patterns.append( self.list_url("ports/$", {"get": "get_ports"}, "vtn_ports") )
        patterns.append( self.list_url("ports/(?P<port_id>[a-zA-Z0-9\-_]+)/$", {"get": "get_port"}, "vtn_port") )
        patterns.append( self.list_url("networks/$", {"get": "get_networks"}, "vtn_networks") )
        patterns.append( self.list_url("networks/(?P<network_id>[a-zA-Z0-9\-_]+)/$", {"get": "get_network"}, "vtn_network") )

        patterns.append( self.list_url("servicePorts/$", {"get": "get_service_ports"}, "vtn_service_ports") )
        patterns.append( self.list_url("servicePorts/(?P<port_id>[a-zA-Z0-9\-_]+)/$", {"get": "get_service_port"}, "vtn_service_port") )
        patterns.append( self.list_url("serviceNetworks/$", {"get": "get_service_networks"}, "vtn_service_networks") )
        patterns.append( self.list_url("serviceNetworks/(?P<network_id>[a-zA-Z0-9\-_]+)/$", {"get": "get_service_network"}, "vtn_service_network") )

        patterns = patterns + super(VTNViewSet,self).get_urlpatterns(api_path)

        return patterns

    def get_services_names(self, request, pk=None):
        result = {}
        for service in Service.objects.all():
           for id in service.get_vtn_src_names():
               dependencies = service.get_vtn_dependencies_names()
               if dependencies:
                   result[id] = dependencies
        return Response(result)

    def get_services(self, request, pk=None):
        result = {}
        for service in Service.objects.all():
           for id in service.get_vtn_src_ids():
               dependencies = service.get_vtn_dependencies_ids()
               if dependencies:
                   result[id] = dependencies
        return Response(result)

    def get_service(self, request, pk=None, service=None):
        for xos_service in Service.objects.all():
            if service in xos_service.get_vtn_src_ids():
                return Response(xos_service.get_vtn_dependencies_ids())
        return Response([])

    def get_ports(self, request):
        result=[]
        for port in Port.objects.all():
            result.append(VTNPortSerializer(VTNPort(port)).data)
        return Response(result)

    def get_port(self, request, port_id=None):
        port = Port.objects.filter(port_id = port_id)
        if port:
            port=port[0]
        else:
            return Response("Failed to find port %s" % port_id, status=status.HTTP_404_NOT_FOUND)
        return Response(VTNPortSerializer(VTNPort(port)).data)

    def get_service_ports(self, request):
        result=[]
        for port in Port.objects.all():
            result.append(VTNServicePortSerializer(VTNPort(port)).data)
        return Response(result)

    def get_service_port(self, request, port_id=None):
        port = Port.objects.filter(port_id = port_id)
        if port:
            port=port[0]
        else:
            return Response("Failed to find port %s" % port_id, status=status.HTTP_404_NOT_FOUND)
        return Response(VTNServicePortSerializer(VTNPort(port)).data)

    def get_networks(self, request):
        result=[]
        for network in Network.objects.all():
            result.append(VTNNetworkSerializer(VTNNetwork(network)).data)
        return Response(result)

    def get_network(self, request, network_id=None):
        network = Network.objects.filter(network_id = network_id)
        if network:
            network=network[0]
        else:
            return Response("Failed to find network %s" % network_id, status=status.HTTP_404_NOT_FOUND)
        return Response(VTNNetworkSerializer(VTNNetwork(network)).data)

    def get_service_networks(self, request):
        result=[]
        for network in Network.objects.all():
            result.append(VTNServiceNetworkSerializer(VTNNetwork(network)).data)
        return Response(result)

    def get_service_network(self, request, network_id=None):
        network = Network.objects.filter(network_id = network_id)
        if network:
            network=network[0]
        else:
            return Response("Failed to find network %s" % network_id, status=status.HTTP_404_NOT_FOUND)
        return Response(VTNServiceNetworkSerializer(VTNNetwork(network)).data)



