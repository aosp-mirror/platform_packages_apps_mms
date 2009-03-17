# Copyright 2007-2008 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := Mms

LOCAL_REQUIRED_MODULES := SoundRecorder

include $(BUILD_PACKAGE)

# This finds and builds the test apk as well, so a single make does both.
include $(call all-makefiles-under,$(LOCAL_PATH))
