
# Copyright 2017-present Open Networking Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


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
from services.vtn.vtnnetport import VTNNetwork, VTNPort
from django.forms import widgets
from django.conf.urls import patterns, url
from api.xosapi_helpers import PlusModelSerializer, XOSViewSet, ReadOnlyField
from django.shortcuts import get_object_or_404
from xos.apibase import XOSListCreateAPIView, XOSRetrieveUpdateDestroyAPIView, XOSPermissionDenied
from xos.exceptions import *
import json
import subprocess

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

class VTNPortSerializer(serializers.Serializer):
    id = ReadOnlyField()
    name = serializers.CharField(required=False)
    network_id = serializers.CharField(required=False)
    network_name = serializers.CharField(required=False)
    mac_address = serializers.CharField(required=False)
    ip_address = serializers.CharField(required=False)

    class Meta:
        fields = ('id', 'name', 'network_id', 'network_name', 'mac_address', 'ip_address')

class FloatingAddressPairSerializer(serializers.Serializer):
    ip_address = ReadOnlyField()
    mac_address = ReadOnlyField()

class VTNServicePortSerializer(serializers.Serializer):
    id = ReadOnlyField()
    vlan_id = serializers.IntegerField(required=False)
    network_name = serializers.CharField(required=False)

    # TODO: structure this better
    floating_address_pairs = serializers.SerializerMethodField("getFloatingAddressPairs")

    class Meta:
        fields = ('id', 'vlan_id', 'network_name')

    def getFloatingAddressPairs(self, obj):
         return obj.floating_address_pairs

class VTNViewSet(XOSViewSet):
    base_name = "vtn"
    method_name = "vtn"
    method_kind = "viewset"

    # these are just because ViewSet needs some queryset and model, even if we don't use the
    # default endpoints
    queryset = VTNService.objects.all()
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
        return Response({"ports": result})

    def get_port(self, request, port_id=None):
        port = Port.objects.filter(port_id = port_id)
        if port:
            port=port[0]
        else:
            return Response("Failed to find port %s" % port_id, status=status.HTTP_404_NOT_FOUND)
        return Response({"port": VTNPortSerializer(VTNPort(port)).data})

    def get_service_ports(self, request):
        result=[]
        for port in Port.objects.all():
            result.append(VTNServicePortSerializer(VTNPort(port)).data)
        return Response({"servicePorts": result})

    def get_service_port(self, request, port_id=None):
        port = Port.objects.filter(port_id = port_id)
        if port:
            port=port[0]
        else:
            return Response("Failed to find port %s" % port_id, status=status.HTTP_404_NOT_FOUND)
        return Response({"servicePort": VTNServicePortSerializer(VTNPort(port)).data})

    def get_networks(self, request):
        result=[]
        for network in Network.objects.all():
            network = VTNNetwork(network)
            if network.id is not None:
                result.append(VTNNetworkSerializer(network).data)
        return Response({"networks": result})

    def get_network(self, request, network_id=None):
        network = [x for x in Network.objects.all() if VTNNetwork(x).id == network_id]
        if network:
            network=network[0]
        else:
            return Response("Failed to find network %s" % network_id, status=status.HTTP_404_NOT_FOUND)
        return Response({"network": VTNNetworkSerializer(VTNNetwork(network)).data})

    def get_service_networks(self, request):
        result=[]
        for network in Network.objects.all():
            network = VTNNetwork(network)
            if network.id is not None:
                result.append(VTNServiceNetworkSerializer(network).data)
        return Response({"serviceNetworks": result})

    def get_service_network(self, request, network_id=None):
        network = [x for x in Network.objects.all() if VTNNetwork(x).id == network_id]
        if network:
            network=network[0]
        else:
            return Response("Failed to find network %s" % network_id, status=status.HTTP_404_NOT_FOUND)
        return Response({"serviceNetwork": VTNServiceNetworkSerializer(VTNNetwork(network)).data})



