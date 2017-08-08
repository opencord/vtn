
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


# This library can be used in two different contexts:
#    1) From the VTN synchronizer
#    2) From the handcrafted VTN API endpoint
#
# If (1) then the modelaccessor module can provide us with models from the API
# or django as appropriate. If (2) then we must use django, until we can
# reconcile what to do about handcrafted API endpoints

import __main__ as main_program

if "synchronizer" in main_program.__file__:
    from synchronizers.new_base.modelaccessor import *
    in_synchronizer = True
else:
    from core.models import *
    in_synchronizer = False

VTN_SERVCOMP_KINDS=["PRIVATE","VSG"]

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
        for dep in service.subscribed_dependencies.all():
            if dep.provider_service:
                bidirectional = dep.connect_method!="private-unidirectional"
                for net in dep.provider_service.get_composable_networks():
                    if not net.controllernetworks.exists():
                        continue

                    cn = net.controllernetworks.all()[0]

                    if not cn.net_id:
                        continue

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
        for dep in service.provided_dependencies.all():
            if dep.subscriber_service:
                bidirectional = dep.connect_method!="private-unidirectional"
                for net in dep.subscriber_service.get_composable_networks():
                    if not net.controllernetworks.exists():
                        continue

                    cn = net.controllernetworks.all()[0]

                    if not cn.net_id:
                        continue

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

    def to_dict(self):
        return {"id": self.id,
                "name": self.name,
                "subnet": self.subnet,
                "gateway": self.gateway,
                "segmentation_id": self.segmentation_id,
                "type": self.type,
                "providerNetworks": self.providerNetworks,
                "subscriberNetworks": self.subscriberNetworks,
                "ownerSliceName": self.ownerSliceName,
                "ownerServiceName": self.ownerServiceName}

    def __eq__(self, other):
        return self.to_dict() == other.to_dict()

class VTNPort(object):
    def __init__(self, xos_port=None):
        self.xos_port = xos_port

    def get_controller_network(self):
        for cn in self.xos_port.network.controllernetworks.all():
            # TODO: find the right one
            return cn
        return None

    def get_vsg_tenants(self):
        # If the VSG service isn't onboarded, then return an empty list.
        if (in_synchronizer):
            if not model_accessor.has_model_class("VSGTenant"):
                 print "VSGTenant model does not exist. Returning no tenants"
                 return []
            VSGTenant = model_accessor.get_model_class("VSGTenant")   # suppress undefined local variable error
        else:
            try:
                from services.vsg.models import VSGTenant
            except ImportError:
                # TODO: Set up logging for this library...
                print "Failed to import VSG, returning no tenants"
                return []

        vsg_tenants=[]
        for tenant in VSGTenant.objects.all():
            if tenant.instance.id == self.xos_port.instance.id:
                vsg_tenants.append(tenant)
        return vsg_tenants

    @property
    def vlan_id(self):
        if not self.xos_port.instance:
            return None

        # Only some kinds of networks can have s-tags associated with them.
        # Currently, only VSG access networks qualify.
        if not self.xos_port.network.template.vtn_kind in ["VSG",]:
            return None

        if (in_synchronizer):
            tags = Tag.objects.filter(content_type=model_accessor.get_content_type_id(self.xos_port.instance),
                                      object_id=self.xos_port.instance.id,
                                      name="s_tag")
        else:
            tags = Tag.select_by_content_object(self.xos_port.instance).filter(name="s_tag")

        if not tags:
            return None

        return tags[0].value

    @property
    def floating_address_pairs(self):
        # Floating_address_pairs is the set of WAN addresses that should be
        # applied to this port.

        address_pairs = []

        # only look apply the VSG addresses if the Network is of the VSG vtn_kind
        if self.xos_port.network.template.vtn_kind in ["VSG", ]:
            for vsg in self.get_vsg_tenants():
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
    def network_name(self):
        return self.xos_port.network.name

    @property
    def mac_address(self):
        return self.xos_port.mac

    @property
    def ip_address(self):
        return self.xos_port.ip

    def to_dict(self):
        return {"id": self.id,
                "name": self.name,
                "network_id": self.network_id,
                "mac_address": self.mac_address,
                "ip_address": self.ip_address,
                "floating_address_pairs": self.floating_address_pairs,
                "vlan_id": self.vlan_id}

    def __eq__(self, other):
        return self.to_dict() == other.to_dict()

