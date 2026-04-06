package io.zerodreamcat.zako.flash;

import io.zerodreamcat.zako.flash.IRemoteProcess;

interface IRemoteService {
    IRemoteProcess getRemoteProcess();
    List<RunningAppProcessInfo> getRunningAppProcesses();
}
