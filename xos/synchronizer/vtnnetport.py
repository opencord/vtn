
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


from synchronizers.new_base.modelaccessor import *
in_synchronizer = True

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

    def is_access_network(self):
        """ Determines whether this port is attached to an access network. Currently we do this by examining the
            network template's vtn_kind field. See if there is a better way...
        """
        return self.xos_port.network.template.vtn_kind in ["VSG", ]

    def get_vm_addresses(self):
        if not self.is_access_network():
            # If not an access network, do not apply any addresses
            return []

        if not self.xos_port.instance:
            return []

        # See if the Instance has any public address (aka "VrouterTenant) service instances associated with it.
        # If so, then add each of those to the set of address pairs.

        # TODO: Perhaps this should be implemented as a link instead of a tag...

        tags = Tag.objects.filter(name="vm_public_service_instance", object_id=self.xos_port.instance.id,
                                            content_type=self.xos_port.instance.self_content_type_id)

        if not tags:
            # DEPRECATED
            # Historically, VSG instances are tagged with "vm_vrouter_tenant" instead of "vm_public_service_instance"
            tags = Tag.objects.filter(name="vm_vrouter_tenant", object_id=self.xos_port.instance.id,
                                                content_type=self.xos_port.instance.self_content_type_id)

        address_pairs = []
        for tag in tags:
            si = ServiceInstance.objects.get(id = int(tag.value))

            # cast from Tenant to descendant class (VRouterTenant, etc)
            si = si.leaf_model

            if (not hasattr(si, "public_ip")) or (not hasattr(si, "public_mac")):
                raise Exception("Object %s does not have public_ip and/or public_mac fields" % si)
            address_pairs.append({"ip_address": si.public_ip,
                                  "mac_address": si.public_mac})

        return address_pairs

    def get_container_addresses(self):
        if not self.is_access_network():
            # If not an access network, do not apply any addresses
            return []

        if not self.xos_port.instance:
            return []

        addrs = []
        for si in ServiceInstance.objects.all():
            # cast from tenant to its descendant class (VSGTenant, etc)
            si = si.leaf_model

            if not hasattr(si, "instance_id"):
                # ignore ServiceInstance that don't have instances
                continue

            if si.instance_id != self.xos_port.instance.id:
                # ignore ServiceInstances that don't relate to our instance
                continue

            # Check to see if there is a link public address (aka VRouterTenant)
            links = si.subscribed_links.all()
            for link in links:
                # cast from ServiceInstance to descendant class (VRouterTenant, etc)
                pubaddr_si = link.provider_service_instance.leaf_model
                if hasattr(pubaddr_si, "public_ip") and hasattr(pubaddr_si, "public_mac"):
                    addrs.append({"ip_address": pubaddr_si.public_ip,
                                  "mac_address": pubaddr_si.public_mac})
        return addrs

    @property
    def vlan_id(self):
        """ Return the vlan_id associated with this instance. This assumes the instance was tagged with either a
            vlan_id or s_tag tag.
        """

        if not self.is_access_network():
            # If not an access network, do not apply any tags
            return []

        if not self.xos_port.instance:
            return None

        tags = Tag.objects.filter(content_type=model_accessor.get_content_type_id(self.xos_port.instance),
                                  object_id=self.xos_port.instance.id,
                                  name="vlan_id")

        if not tags:
            # DEPRECATED
            # Historically, VSG instances are tagged with "s_tag" instead of "vlan_id"
            tags = Tag.objects.filter(content_type=model_accessor.get_content_type_id(self.xos_port.instance),
                                      object_id=self.xos_port.instance.id,
                                      name="s_tag")

        if not tags:
            return None

        return tags[0].value

    @property
    def floating_address_pairs(self):
        # Floating_address_pairs is the set of WAN addresses that should be
        # applied to this port.

        # We only want to apply these addresses to an "access" network.


        address_pairs = self.get_vm_addresses() + self.get_container_addresses()

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

