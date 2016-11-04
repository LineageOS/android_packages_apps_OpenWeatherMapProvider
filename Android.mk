LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := OpenWeatherMapProvider
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := OpenWeatherMapProvider

openmap_root  := $(LOCAL_PATH)
openmap_dir   := app
openmap_out   := $(OUT_DIR)/target/common/obj/APPS/$(LOCAL_MODULE)_intermediates
openmap_build := $(openmap_root)/$(openmap_dir)/build
openmap_apk   := build/outputs/apk/$(openmap_dir)-release-unsigned.apk

$(openmap_root)/$(openmap_dir)/$(openmap_apk):
	rm -Rf $(openmap_build)
	mkdir -p $(openmap_out)
	ln -sf $(openmap_out) $(openmap_build)
	cd $(openmap_root)/$(openmap_dir) && JAVA_TOOL_OPTIONS="$(JAVA_TOOL_OPTIONS) -Dfile.encoding=UTF8" ../gradlew assembleRelease

LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(openmap_dir)/$(openmap_apk)
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
