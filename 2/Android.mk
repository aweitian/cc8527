LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := qep
LOCAL_SRC_FILES := \
	main.c 	\
	log.c
LOCAL_LDLIBS    := -llog -lGLESv2
include $(BUILD_EXECUTABLE)
