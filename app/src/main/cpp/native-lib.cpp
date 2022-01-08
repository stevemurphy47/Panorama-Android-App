#include "opencv2/highgui.hpp"
#include "opencv2/stitching.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/core.hpp"

#include <jni.h>
#include <iostream>
#include <android/bitmap.h>


using namespace std;
using namespace cv;

void bitmapToMat(JNIEnv *env, jobject bitmap, Mat &dst, jboolean needUnPremultiplyAlpha);
void matToBitmap(JNIEnv *env, Mat src, jobject bitmap, jboolean needPremultiplyAlpha);

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_android_example_panoramacamera_fragments_StitcherFragment_processPanorama(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jobjectArray image_in_) {
    // TODO: implement processPanorama()
    // Get the length of the long array
    jsize image_in_len = env->GetArrayLength(image_in_);
    // Create a vector to store all the image
    vector<Mat> imgVec;
    Mat oldImageIn;
    for(int k=0; k < image_in_len; k++){
        //jobject image = env -> GetObjectArrayElement(image_in_, k);
        bitmapToMat(env, env -> GetObjectArrayElement(image_in_, k), oldImageIn, false);
        Mat newImageIn;

        // Convert to a 3 channel Mat to use with Stitcher module
        cvtColor(oldImageIn, newImageIn, COLOR_RGB2GRAY);

        // Reduce the resolution for fast computation
        int scale = 50;
        resize(newImageIn, newImageIn, Size((int)(newImageIn.rows * scale / 100), (int)(newImageIn.cols * scale / 100)));

        imgVec.push_back(newImageIn);
    }

    Mat pano;
    Stitcher::Mode mode = Stitcher::PANORAMA;
    Ptr<Stitcher> stitcher = createStitcher(false);
    Stitcher::Status state = stitcher->stitch(imgVec, pano);

    jintArray jint_arr = env->NewIntArray(4);
    jint *elems = env->GetIntArrayElements(jint_arr, nullptr);
    elems[0] = state;//status code
    elems[1] = pano.cols;//wide
    elems[2] = pano.rows;//high
    elems[3] = imgVec.size();

    env->ReleaseIntArrayElements(jint_arr, elems, 0);
    return jint_arr;
}

void bitmapToMat(JNIEnv *env, jobject bitmap, Mat &dst, jboolean needUnPremultiplyAlpha) {
    AndroidBitmapInfo  info;
    void*              pixels = 0;

    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        dst.create(info.height, info.width, CV_8UC4);
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(needUnPremultiplyAlpha) cvtColor(tmp, dst, COLOR_mRGBA2RGBA);
            else tmp.copyTo(dst);
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            Mat tmp(info.height, info.width, CV_8UC2, pixels);
            cvtColor(tmp, dst, COLOR_BGR5652RGBA);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nBitmapToMat}");
        return;
    }

}

void matToBitmap(JNIEnv *env, Mat src, jobject bitmap, jboolean needPremultiplyAlpha) {
    AndroidBitmapInfo  info;
    void*              pixels = 0;

    try {
        CV_Assert( AndroidBitmap_getInfo(env, bitmap, &info) >= 0 );
        CV_Assert( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                   info.format == ANDROID_BITMAP_FORMAT_RGB_565 );
        CV_Assert( src.dims == 2 && info.height == (uint32_t)src.rows && info.width == (uint32_t)src.cols );
        CV_Assert( src.type() == CV_8UC1 || src.type() == CV_8UC3 || src.type() == CV_8UC4 );
        CV_Assert( AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 );
        CV_Assert( pixels );
        if( info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 )
        {
            Mat tmp(info.height, info.width, CV_8UC4, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, COLOR_GRAY2RGBA);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, COLOR_RGB2RGBA);
            } else if(src.type() == CV_8UC4){
                if(needPremultiplyAlpha) cvtColor(src, tmp, COLOR_RGBA2mRGBA);
                else src.copyTo(tmp);
            }
        } else {
            // info.format == ANDROID_BITMAP_FORMAT_RGB_565
            Mat tmp(info.height, info.width, CV_8UC2, pixels);
            if(src.type() == CV_8UC1)
            {
                cvtColor(src, tmp, COLOR_GRAY2BGR565);
            } else if(src.type() == CV_8UC3){
                cvtColor(src, tmp, COLOR_RGB2BGR565);
            } else if(src.type() == CV_8UC4){
                cvtColor(src, tmp, COLOR_RGBA2BGR565);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    } catch(const cv::Exception& e) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
        return;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, "Unknown exception in JNI code {nMatToBitmap}");
        return;
    }
}
