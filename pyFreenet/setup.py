#!/usr/bin/env python
#@+leo
#@+node:0::@file setup.py
#@+body
#@@first
#@@language python

# This file does the python-specific build and install
# for pyFreenet
#
# Written David McNab, 11 Feb 2003

import sys,os
from distutils.core import setup, Extension

setup(name='freenet',
	  version = '0.1',
	  description = 'pyFreenet - Python API for Freenet access',
	  py_modules = ['freenet']
	  )


#@-body
#@-node:0::@file setup.py
#@-leo
