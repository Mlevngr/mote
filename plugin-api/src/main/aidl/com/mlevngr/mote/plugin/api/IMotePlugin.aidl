package com.mlevngr.mote.plugin.api;

import android.os.Bundle;
import com.mlevngr.mote.plugin.api.IMotePluginCallback;

interface IMotePlugin {
    Bundle getDescriptor();
    void execute(in Bundle request, IMotePluginCallback callback);
    void cancel(String requestId);
}
