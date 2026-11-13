package in.pgease.owner;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String PREFS_NAME = "pgease_mobile_prefs";
    private static final String KEY_ONBOARDING_SEEN = "has_seen_onboarding_slides";
    private final int[] slides = { R.drawable.slide_1, R.drawable.slide_2, R.drawable.slide_3 };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean hasSeen = prefs.getBoolean(KEY_ONBOARDING_SEEN, false);

        if (!hasSeen) {
            setupOnboardingOverlay(prefs);
        }
    }

    private void setupOnboardingOverlay(final SharedPreferences prefs) {
        final ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        if (rootView == null) return;

        final View overlay = LayoutInflater.from(this).inflate(R.layout.onboarding_overlay, rootView, false);
        rootView.addView(overlay);

        final ViewPager viewPager = overlay.findViewById(R.id.onboarding_viewpager);
        final TextView btnSkip = overlay.findViewById(R.id.btn_skip);
        final Button btnAction = overlay.findViewById(R.id.btn_action);
        final LinearLayout dotsLayout = overlay.findViewById(R.id.dots_layout);

        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return slides.length;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                ImageView imageView = new ImageView(MainActivity.this);
                imageView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageResource(slides[position]);
                container.addView(imageView);
                return imageView;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }
        });

        updateDots(dotsLayout, 0);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                updateDots(dotsLayout, position);
                if (position == slides.length - 1) {
                    btnAction.setText("Get Started");
                } else {
                    btnAction.setText("Next");
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        View.OnClickListener finishAction = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply();
                overlay.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        rootView.removeView(overlay);
                    }
                });
            }
        };

        btnSkip.setOnClickListener(finishAction);

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = viewPager.getCurrentItem();
                if (current < slides.length - 1) {
                    viewPager.setCurrentItem(current + 1, true);
                } else {
                    prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply();
                    overlay.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            rootView.removeView(overlay);
                        }
                    });
                }
            }
        });
    }

    private void updateDots(LinearLayout dotsLayout, int current) {
        dotsLayout.removeAllViews();
        for (int i = 0; i < slides.length; i++) {
            View dot = new View(this);
            int width = (i == current) ? dpToPx(24) : dpToPx(7);
            int height = dpToPx(7);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMargins(dpToPx(3), 0, dpToPx(3), 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == current ? R.drawable.dot_active : R.drawable.dot_inactive);
            dotsLayout.addView(dot);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
