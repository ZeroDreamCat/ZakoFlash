package io.zerodreamcat.zako.flash;

interface IRemoteProcess {
    ParcelFileDescriptor  getOutputStream();
    ParcelFileDescriptor getInputStream();
    ParcelFileDescriptor getErrorStream();
    int waitFor();
    int exitValue();
    void destroy();
    boolean isAlive();
}
