from django.db import models
from django.db.models import *
from core.models import Service
from core.models.xosbase import StrippedCharField
import os
from django.db import models, transaction
from django.forms.models import model_to_dict
import traceback
from xos.exceptions import *

class ConfigurationError(Exception):
    pass

VTN_KIND = "VTN"
