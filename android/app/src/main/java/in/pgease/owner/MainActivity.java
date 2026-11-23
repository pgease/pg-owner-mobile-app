package in.pgease.owner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final String PREFS_NAME = "pgease_mobile_prefs";
    private static final String KEY_ONBOARDING_SEEN = "has_seen_onboarding_slides";
    private final int[] slides = { R.drawable.slide_1, R.drawable.slide_2, R.drawable.slide_3 };

    private OwnerShell shell;
    private View onboardingOverlay;
    private ViewPager onboardingPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        CookieManager.getInstance().setAcceptCookie(true);
        SystemBarStyling.enable(this);
        super.onCreate(savedInstanceState);
        removeTitleBar();
        SystemBarStyling.enable(this);
        getWindow().getDecorView().setBackgroundColor(SystemBarStyling.BRAND);

        shell = new OwnerShell(this);
        if (getBridge() != null) {
            shell.install();
            shell.openAppLink(getIntent());
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean hasSeen = prefs.getBoolean(KEY_ONBOARDING_SEEN, false);
        if (!hasSeen) {
            setupOnboardingOverlay(prefs);
        }

        getOnBackPressedDispatcher().addCallback(
            this,
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (onboardingOverlay != null && onboardingOverlay.getParent() != null && onboardingPager != null) {
                        int current = onboardingPager.getCurrentItem();
                        if (current > 0) {
                            onboardingPager.setCurrentItem(current - 1, true);
                        }
                        return;
                    }
                    if (shell != null) {
                        shell.onBackPressed();
                    }
                }
            }
        );
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        removeTitleBar();
    }

    @Override
    public void onResume() {
        super.onResume();
        removeTitleBar();
    }

    private void removeTitleBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        int containerId = getResources().getIdentifier("action_bar_container", "id", "android");
        if (containerId != 0) {
            View actionBar = findViewById(containerId);
            if (actionBar != null && actionBar.getParent() instanceof ViewGroup) {
                ((ViewGroup) actionBar.getParent()).removeView(actionBar);
            } else if (actionBar != null) {
                actionBar.setVisibility(View.GONE);
            }
        }
        View decor = getWindow().getDecorView();
        ViewCompat.requestApplyInsets(decor);
        decor.post(() -> ViewCompat.requestApplyInsets(decor));
    }

    @Override
    public void onPause() {
        if (shell != null) {
            shell.flushCookies();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shell != null) {
            shell.openAppLink(intent);
        }
    }

    private void setupOnboardingOverlay(final SharedPreferences prefs) {
        final ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }

        final View overlay = LayoutInflater.from(this).inflate(R.layout.onboarding_overlay, rootView, false);
        onboardingOverlay = overlay;
        rootView.addView(overlay);
        if (shell != null) {
            shell.setOnboardingView(overlay);
        }
        applyOnboardingInsets(overlay);

        final ViewPager viewPager = overlay.findViewById(R.id.onboarding_viewpager);
        onboardingPager = viewPager;
        final TextView btnSkip = overlay.findViewById(R.id.btn_skip);
        final Button btnAction = overlay.findViewById(R.id.btn_action);
        final LinearLayout dotsLayout = overlay.findViewById(R.id.dots_layout);

        viewPager.setAdapter(
            new PagerAdapter() {
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
                    imageView.setLayoutParams(
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    );
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setImageResource(slides[position]);
                    container.addView(imageView);
                    return imageView;
                }

                @Override
                public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                    container.removeView((View) object);
                }
            }
        );

        updateDots(dotsLayout, 0);

        viewPager.addOnPageChangeListener(
            new ViewPager.OnPageChangeListener() {
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
            }
        );

        View.OnClickListener finishAction = v -> dismissOnboarding(prefs, rootView, overlay);

        btnSkip.setOnClickListener(finishAction);
        btnAction.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < slides.length - 1) {
                viewPager.setCurrentItem(current + 1, true);
            } else {
                dismissOnboarding(prefs, rootView, overlay);
            }
        });
    }

    private void dismissOnboarding(SharedPreferences prefs, ViewGroup rootView, View overlay) {
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply();
        overlay
            .animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction(() -> {
                rootView.removeView(overlay);
                if (onboardingOverlay == overlay) {
                    onboardingOverlay = null;
                    onboardingPager = null;
                }
                if (shell != null) {
                    shell.setOnboardingView(null);
                }
            });
    }

    private void applyOnboardingInsets(View overlay) {
        View topBar = overlay.findViewById(R.id.onboarding_top);
        View bottomBar = overlay.findViewById(R.id.onboarding_bottom);
        int topBase = topBar.getPaddingTop();
        int topLeft = topBar.getPaddingLeft();
        int topRight = topBar.getPaddingRight();
        int topBottom = topBar.getPaddingBottom();
        int bottomBase = bottomBar.getPaddingBottom();
        int bottomLeft = bottomBar.getPaddingLeft();
        int bottomRight = bottomBar.getPaddingRight();
        int bottomTop = bottomBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topBar.setPadding(topLeft, topBase + bars.top, topRight, topBottom);
            bottomBar.setPadding(bottomLeft, bottomTop, bottomRight, bottomBase + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(overlay);
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
