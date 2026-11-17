#include <NeuralNetworks.h>
#include <stdio.h>
int main(){uint32_t n=0;int r=ANeuralNetworks_getDeviceCount(&n);printf("status=%d count=%u\n",r,n);for(uint32_t i=0;i<n;i++){ANeuralNetworksDevice*d;const char*name="",*v="";int32_t type=-1;int64_t level=0;ANeuralNetworks_getDevice(i,&d);ANeuralNetworksDevice_getName(d,&name);ANeuralNetworksDevice_getVersion(d,&v);ANeuralNetworksDevice_getType(d,&type);ANeuralNetworksDevice_getFeatureLevel(d,&level);printf("name=%s type=%d feature=%lld version=%s\n",name,type,(long long)level,v);} }

