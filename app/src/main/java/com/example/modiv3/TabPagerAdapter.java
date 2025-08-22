package com.example.modiv3;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TabPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 2;

    public TabPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new CollectionFragment();
            case 1:
                return new LoggerFragment();
            default:
                return new CollectionFragment();
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }

    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return "Collection";
            case 1:
                return "Logger";
            default:
                return "Tab " + (position + 1);
        }
    }
}
