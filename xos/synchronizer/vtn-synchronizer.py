#!/usr/bin/env python

# This imports and runs ../../xos-observer.py

import importlib
import os
import sys
sys.path.append('/opt/xos')

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "xos.settings")

mod = importlib.import_module("synchronizers.new_base.xos-synchronizer")
mod.main()

