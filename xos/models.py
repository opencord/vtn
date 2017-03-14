from django.db import models
from core.models import Service
from core.models.plcorebase import StrippedCharField
import os
from django.db import models, transaction
from django.forms.models import model_to_dict
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
    sshUser = StrippedCharField(max_length=30, default="root")
    sshKeyFile = StrippedCharField(max_length=1024, default="/root/node_key")
    mgmtSubnetBits = models.IntegerField(default=24)
    xosEndpoint = StrippedCharField(max_length=1024, default="http://xos/")
    xosUser = StrippedCharField(max_length=255, default="padmin@vicci.org")
    xosPassword = StrippedCharField(max_length=255, default="letmein")
    vtnAPIVersion = models.IntegerField(default=1)
    controllerPort = StrippedCharField(max_length=255, default="onos-cord:6653")

