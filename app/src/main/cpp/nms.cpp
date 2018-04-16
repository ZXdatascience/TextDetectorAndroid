//
// Created by xu on 04/03/18.
//

#include <jni.h>
#include "lanms.h"

namespace res {
    std::vector<float> polys2floats(const std::vector<lanms::Polygon> &polys) {
        std::vector<float> ret;
        for (size_t i = 0; i < polys.size(); i ++) {
            auto &p = polys[i];
            auto &poly = p.poly;
            for (int i = 0; i < 4; i++) {
                ret.emplace_back(float(poly[i].X));
                ret.emplace_back(float(poly[i].Y));
            }
            ret.emplace_back(p.score);

//            ret.emplace_back(
//                    float(poly[0].X), float(poly[0].Y),
//                    float(poly[1].X), float(poly[1].Y),
//                    float(poly[2].X), float(poly[2].Y),
//                    float(poly[3].X), float(poly[3].Y),
//                    float(p.score)
//            );
        }

        return ret;
    }
}


extern "C"
JNIEXPORT jfloatArray

JNICALL
Java_com_example_xu_menupro_ZoomageView_nms(JNIEnv *env, jobject thisObj, jfloatArray inJNIArray, jint n, jfloat thres) {
    jfloat *inCArray = env->GetFloatArrayElements(inJNIArray, NULL);
    if (NULL == inCArray) return NULL;
    std::vector<float> afterNMSCpp = res::polys2floats(lanms::merge_quadrangle_n9(inCArray, n, thres));
    jfloatArray resArray = env->NewFloatArray(afterNMSCpp.size());

    env->SetFloatArrayRegion(resArray, 0, afterNMSCpp.size(), &afterNMSCpp[0]);
    return resArray;
}

