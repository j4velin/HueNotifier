package de.j4velin.huenotifier;

import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.os.Build;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.M)
class API23Wrapper {

    static void setCompoundDrawableTintList(final TextView view, int color) {
        view.setCompoundDrawableTintList(ColorStateList.valueOf(color));
    }

}
