#include <jni.h>
#include <string>
#include "lamemp3/lame.h"
#include <sys/stat.h>

#define INBUFSIZE 4096
#define MP3BUFSIZE (int) (1.25 * INBUFSIZE) + 7200

extern "C"
JNIEXPORT void JNICALL
Java_com_hyq_hm_lame_SimpleLame_convert(JNIEnv *env, jclass type, jobject listener,jint index,
                                                   jstring jwav_,jstring jmp3_,
                                                   jint inSampleRate,jint outChannel,
                                                   jint outSampleRate,jint outBitrate,
                                                   jint quality) {
    const char *jwav = env->GetStringUTFChars(jwav_, 0);
    const char *jmp3 = env->GetStringUTFChars(jmp3_, 0);
    // TODO
    short int wav_buffer[INBUFSIZE*outChannel];
    unsigned char mp3_buffer[MP3BUFSIZE];
//    获取文件大小
    struct stat st;
    stat(jwav, &st );
    jclass cls = env->GetObjectClass(listener);
    jmethodID mid = env->GetMethodID(cls, "setProgress", "(JJI)V");

    FILE* fwav = fopen(jwav,"rb");
    FILE* fmp3 = fopen(jmp3,"wb");
    lame_t lameConvert =  lame_init();

    lame_set_in_samplerate(lameConvert , inSampleRate);
    lame_set_out_samplerate(lameConvert, outSampleRate);
    lame_set_num_channels(lameConvert,outChannel);
//    lame_set_VBR(lameConvert,vbr_mtrh);
//    lame_set_VBR_mean_bitrate_kbps(lameConvert,outBitrate);
    lame_set_brate(lameConvert,outBitrate);
    lame_set_quality(lameConvert, quality);
    lame_init_params(lameConvert);

    int read ; int write;
    long total=0;
    do{
        read = (int) fread(wav_buffer, sizeof(short int) * outChannel, INBUFSIZE, fwav);
        total +=  read* sizeof(short int)*outChannel;
        env->CallVoidMethod(listener,mid,(long)st.st_size,total,index);
        if(read!=0){
            if (outChannel == 2){
                write = lame_encode_buffer_interleaved(lameConvert,wav_buffer,read,mp3_buffer,MP3BUFSIZE);
            }else{
                write = lame_encode_buffer(lameConvert,wav_buffer,wav_buffer,read,mp3_buffer,MP3BUFSIZE);
            }
        } else{
            write = lame_encode_flush(lameConvert,mp3_buffer,MP3BUFSIZE);
        }
        fwrite(mp3_buffer, sizeof(unsigned char), (size_t) write, fmp3);
    }while (read!=0);
    lame_mp3_tags_fid(lameConvert,fmp3);
    lame_close(lameConvert);
    fclose(fwav);
    fclose(fmp3);

    env->ReleaseStringUTFChars(jwav_, jwav);
    env->ReleaseStringUTFChars(jmp3_, jmp3);
}