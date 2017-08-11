# VTN development notes #

## Public Address ServiceInstances ##

Hardcoded dependencies to `VRouterTenant` have been eliminated. It's now assumed that any ServiceInstance that has `public_ip` and `public_mac` fields provides the addressing functionality that VRouterTenant used to provide. 

## Determining Additional Address Pairs ##

The VTN synchronizer will compute additional addresses attached to a port based on the following criteria:

1) If an instance has an `vm_vrouter_tag` or `vm_public_service_instance` tag attached to it, and that tag points to a ServiceInstance that has `public_ip` and `public_mac` fields, then that address pair will be added to the ports for any access networks on that instance. (TODO: replace tag with link?)

`vm_vrouter_tag` is deprecated in favor of the service-neutron name `vm_public_service_instance`. 

2) If there exists a tenant associated with an instance, and that tenant has a `SerivceInstanceLink` to a ServiceInstance that has `public_ip` and `public_mac` fields, then that address pair will be added to the ports for any access networks on that instance.

## Determining vlan_id ##

A port will be given a `vlan_id` if there exists a `vlan_id` or `s_tag` tag associated with the instance, and that port is an access network.

`s_tag` is deprecated in favor of the service-neutral name `vlan_id`. 

## Determining access networks ##

A network is an access network (i.e. supports vlan_id and address_pairs) if it's VTN kind is in the 
set `["VSG", ]`. (TODO: Find a better way to mark address networks)

## Determining Public Gateways ##

The VTN synchronizer determines public gateways by examining `AddressPool` objects. Each `AddressPool` has a `gateway_ip` and `gateway_mac` field. 