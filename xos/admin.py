from django.contrib import admin

from django import forms
from django.utils.safestring import mark_safe
from django.contrib.auth.admin import UserAdmin
from django.contrib.admin.widgets import FilteredSelectMultiple
from django.contrib.auth.forms import ReadOnlyPasswordHashField
from django.contrib.auth.signals import user_logged_in
from django.utils import timezone
from django.contrib.contenttypes import generic
from suit.widgets import LinkedSelect
from core.admin import ServiceAppAdmin,SliceInline,ServiceAttrAsTabInline, ReadOnlyAwareAdmin, XOSTabularInline, ServicePrivilegeInline, TenantRootTenantInline, TenantRootPrivilegeInline
from core.middleware import get_request

from services.vtn.models import *

from functools import update_wrapper
from django.contrib.admin.views.main import ChangeList
from django.core.urlresolvers import reverse
from django.contrib.admin.utils import quote

class VTNServiceAdmin(ReadOnlyAwareAdmin):
    model = VTNService
    verbose_name = "VTN Service"
    verbose_name_plural = "VTN Service"
    list_display = ("backend_status_icon", "name", "enabled")
    list_display_links = ('backend_status_icon', 'name', )
    fieldsets = [(None, {'fields': ['backend_status_text', 'name','enabled','versionNumber','description',"view_url","icon_url",
                                    'privateGatewayMac', 'localManagementIp', 'ovsdbPort', 'sshPort', 'sshUser', 'sshKeyFile', 'mgmtSubnetBits', 'xosEndpoint', 'xosUser', 'xosPassword', 'vtnAPIVersion', 'controllerPort' ], 'classes':['suit-tab suit-tab-general']})]
    readonly_fields = ('backend_status_text', )
    inlines = [SliceInline,ServiceAttrAsTabInline,ServicePrivilegeInline]

    extracontext_registered_admins = True

    user_readonly_fields = ["name", "enabled", "versionNumber", "description"]

    suit_form_tabs =(('general', 'VTN Service Details'),
#        ('administration', 'Administration'),
        ('slices','Slices'),
        ('serviceattrs','Additional Attributes'),
        ('serviceprivileges','Privileges'),
    )

    suit_form_includes = ( # ('vtnadmin.html', 'top', 'administration'),
                           ) #('hpctools.html', 'top', 'tools') )

    def get_queryset(self, request):
        return VTNService.get_service_objects_by_user(request.user)

admin.site.register(VTNService, VTNServiceAdmin)
