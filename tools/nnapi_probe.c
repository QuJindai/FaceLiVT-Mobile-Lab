#include <stdint.h>
#include <dlfcn.h>
typedef void ANeuralNetworksDevice;
#define LOAD(name, args) int (*name) args = dlsym(h, #name)
#include <stdio.h>
int main(){void*h=dlopen("libneuralnetworks.so",RTLD_NOW);if(!h){puts(dlerror());return 1;} LOAD(ANeuralNetworks_getDeviceCount,(uint32_t*)); LOAD(ANeuralNetworks_getDevice,(uint32_t,ANeuralNetworksDevice**)); LOAD(ANeuralNetworksDevice_getName,(ANeuralNetworksDevice*,const char**)); LOAD(ANeuralNetworksDevice_getVersion,(ANeuralNetworksDevice*,const char**)); LOAD(ANeuralNetworksDevice_getType,(ANeuralNetworksDevice*,int32_t*)); LOAD(ANeuralNetworksDevice_getFeatureLevel,(ANeuralNetworksDevice*,int64_t*));uint32_t n=0;int r=ANeuralNetworks_getDeviceCount(&n);printf("status=%d count=%u\n",r,n);for(uint32_t i=0;i<n;i++){ANeuralNetworksDevice*d;const char*name="",*v="";int32_t type=-1;int64_t level=0;ANeuralNetworks_getDevice(i,&d);ANeuralNetworksDevice_getName(d,&name);ANeuralNetworksDevice_getVersion(d,&v);ANeuralNetworksDevice_getType(d,&type);ANeuralNetworksDevice_getFeatureLevel(d,&level);printf("name=%s type=%d feature=%lld version=%s\n",name,type,(long long)level,v);} }


