package de.j4velin.huenotifier;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import androidx.recyclerview.widget.RecyclerView;

import static de.j4velin.huenotifier.R.layout.rule;

/**
 * The UI adapter containing all the rules
 */
class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.ViewHolder> {

    private final MainActivity activity;
    private final LayoutInflater inflater;
    private final PackageManager pm;
    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            View edit = view.findViewById(R.id.edit);
            edit.setMinimumHeight(view.findViewById(R.id.lights).getHeight());
            MainActivity.fadeView(true, edit);
        }
    };
    private final View.OnClickListener configureClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            View editView = (View) view.getParent();
            if (activity.isConnected) {
                View cardView = (View) editView.getParent();
                final int itemPosition = activity.ruleList.getChildLayoutPosition(cardView);
                activity.editRule(activity.rules.get(itemPosition));
            } else {
                Snackbar.make(activity.findViewById(android.R.id.content), R.string.not_connected,
                        Snackbar.LENGTH_SHORT).show();
            }
            MainActivity.fadeView(false, editView);
        }
    };
    private final View.OnClickListener deleteClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            View cardView = (View) view.getParent().getParent();
            final int itemPosition = activity.ruleList.getChildLayoutPosition(cardView);
            Rule r = activity.rules.remove(itemPosition);
            activity.ruleAdapter.notifyItemRemoved(itemPosition);
            Database db = Database.getInstance(activity);
            db.delete(r.appPkg, r.person);
            db.close();
        }
    };
    private final View.OnClickListener cancelClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            View editView = (View) view.getParent();
            MainActivity.fadeView(false, editView);
        }
    };

    RuleAdapter(MainActivity activity) {
        this.activity = activity;
        inflater = LayoutInflater.from(activity);
        pm = activity.getPackageManager();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = inflater.inflate(rule, parent, false);
        v.setOnClickListener(clickListener);
        ViewHolder holder = new ViewHolder(v);
        holder.edit.findViewById(R.id.configure).setOnClickListener(configureClickListener);
        holder.edit.findViewById(R.id.delete).setOnClickListener(deleteClickListener);
        holder.edit.findViewById(R.id.cancel).setOnClickListener(cancelClickListener);
        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Rule rule = activity.rules.get(position);
        holder.text.setText(rule.appName);
        holder.edit.setVisibility(View.GONE);
        Drawable appIcon;
        try {
            appIcon = pm.getApplicationIcon(rule.appPkg);
            appIcon.setBounds(0, 0, Util.dpToPx(activity, 25),
                    Util.dpToPx(activity, 25));
        } catch (PackageManager.NameNotFoundException e) {
            appIcon = null;
        }
        holder.text.setCompoundDrawables(appIcon, null, null, null);
        holder.linearLayout.removeAllViews();
        for (int i = 0; i < rule.lightSettings.lights.length; i++) {
            TextView light = (TextView) inflater
                    .inflate(R.layout.light, holder.linearLayout, false);
            int lightIcon;
            if (activity.lights != null && activity.lights.containsKey(
                    String.valueOf(rule.lightSettings.lights[i]))) {
                Light lightObject = activity.lights.get(
                        String.valueOf(rule.lightSettings.lights[i]));
                light.setText(lightObject.name);
                lightIcon = Util.getLightIcon(lightObject.modelid);
            } else {
                light.setText("Light #" + rule.lightSettings.lights[i]);
                lightIcon = R.drawable.ic_light;
            }
            light.setCompoundDrawablesWithIntrinsicBounds(lightIcon, 0, 0, 0);
            if (Build.VERSION.SDK_INT >= 23) {
                API23Wrapper.setCompoundDrawableTintList(light, rule.lightSettings.colors[i]);
            }
            light.setTextColor(rule.lightSettings.colors[i]);
            holder.linearLayout.addView(light);
        }
    }

    @Override
    public int getItemCount() {
        return activity.rules.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final LinearLayout linearLayout;
        final View edit;

        private ViewHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.app);
            linearLayout = (LinearLayout) itemView.findViewById(R.id.lights);
            edit = itemView.findViewById(R.id.edit);
        }
    }
}