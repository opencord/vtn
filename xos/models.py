from django.db import models
from core.models import Service, PlCoreBase, Slice, Instance, Tenant, TenantWithContainer, Node, Image, User, Flavor, Subscriber, NetworkParameter, NetworkParameterType, Port, AddressPool
from core.models.plcorebase import StrippedCharField
import os
from django.db import models, transaction
from django.forms.models import model_to_dict
from django.db.models import Q
from operator import itemgetter, attrgetter, methodcaller
from core.models import Tag
from core.models.service import LeastLoadedNodeScheduler
import traceback
from xos.exceptions import *
from xos.config import Config

class ConfigurationError(Exception):
    pass

VTN_KIND = "VTN"

# -------------------------------------------
# VTN
# -------------------------------------------

class VTNService(Service):
    KIND = VTN_KIND

    class Meta:
        app_label = "vtn"
        verbose_name = "VTN Service"

    privateGatewayMac = StrippedCharField(max_length=30, default="00:00:00:00:00:01")
    localManagementIp = StrippedCharField(max_length=30, default="172.27.0.1/24")
    ovsdbPort = models.IntegerField(default=6641)
    sshPort = models.IntegerField(default=22)
    sshUser = models.StrippedCharField(max_length=30, default="root")
    mgmtSubnetBits = models.IntegerField(default=24)
    xosEndpoint = models.StippedCharField(max_length=30, default="http://xos/")
    xosUser = models.StrippedCharField(max_length=255, default="padmin@vicci.org")
    xosPassword = models.StrippedCharField(max_length=255, default="letmein")
    vtnApiVersion = models.IntegerField(default=1)
    controllerPort = models.StrippedCharField(max_length=255, default="onos-cord:6653")

