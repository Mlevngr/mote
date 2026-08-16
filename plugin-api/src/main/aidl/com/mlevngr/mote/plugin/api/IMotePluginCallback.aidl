package com.mlevngr.mote.plugin.api;

import android.os.Bundle;

oneway interface IMotePluginCallback {
    void onResult(in Bundle result);
    void onError(in Bundle error);
}
