from core.models import *
from services.vtn.models import VTNService

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
        for tenant in service.subscribed_tenants.all():
            if tenant.provider_service:
                bidirectional = tenant.connect_method!="private-unidirectional"
                for net in tenant.provider_service.get_composable_networks():
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
        for tenant in service.provided_tenants.all():
            if tenant.subscriber_service:
                bidirectional = tenant.connect_method!="private-unidirectional"
                for net in tenant.subscriber_service.get_composable_networks():
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
        # Only some kinds of networks can have s-tags associated with them.
        # Currently, only VSG access networks qualify.
        if not self.xos_port.network.template.vtn_kind in ["VSG",]:
            return None
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

