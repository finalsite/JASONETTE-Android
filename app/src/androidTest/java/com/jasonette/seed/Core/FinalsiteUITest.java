package com.jasonette.seed.Core;


import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.jasonette.seed.R;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.ScreenshotCallback;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FinalsiteUITest {

    @ClassRule
    public static final LocaleTestRule localeTestRule = new LocaleTestRule();

    @Rule
    public ActivityTestRule<SplashActivity> mActivityTestRule = new ActivityTestRule<>(SplashActivity.class);

    @BeforeClass
    public static void setUp() {
        Screengrab.setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());
    }

    @Test
    public void ScreenshotTest() {
        appSleep(4000);
        Screengrab.screenshot("01HomeScreen");

        ViewInteraction frameLayout2 = onView(
                allOf(withId(R.id.bottom_navigation_container),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.jason_bottom_navigation),
                                        1),
                                1),
                        isDisplayed()));
        frameLayout2.perform(click());

        appSleep(4000);
        Screengrab.screenshot("02TabScreen");

        ViewInteraction frameLayout3 = onView(
                allOf(withId(R.id.bottom_navigation_container),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.jason_bottom_navigation),
                                        1),
                                2),
                        isDisplayed()));
        frameLayout3.perform(click());

        appSleep(4000);
        Screengrab.screenshot("03TabScreen");

        ViewInteraction frameLayout4 = onView(
                allOf(withId(R.id.bottom_navigation_container),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.jason_bottom_navigation),
                                        1),
                                3),
                        isDisplayed()));
        frameLayout4.perform(click());

        appSleep(4000);
        Screengrab.screenshot("04TabScreen");

        ViewInteraction frameLayout5 = onView(
                allOf(withId(R.id.bottom_navigation_container),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.jason_bottom_navigation),
                                        1),
                                4),
                        isDisplayed()));
        frameLayout5.perform(click());

        appSleep(4000);
        Screengrab.screenshot("05TabScreen");
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }

    private static void appSleep(int millis){
        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try{
            Thread.sleep(millis);
        }catch(InterruptedException e){
            System.out.println("got interrupted!");
        }
    }
}
