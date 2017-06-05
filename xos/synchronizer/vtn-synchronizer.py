#!/usr/bin/env python

# This imports and runs ../../xos-observer.py

import importlib
import os
import sys
from xosconfig import Config

config_file = os.path.abspath(os.path.dirname(os.path.realpath(__file__)) + '/vtn_config.yaml')
Config.init(config_file, 'synchronizer-config-schema.yaml')

sys.path.append('/opt/xos')

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "xos.settings")

mod = importlib.import_module("synchronizers.new_base.xos-synchronizer")
mod.main()

