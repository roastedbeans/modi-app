package com.example.modiv3;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.example.modiv3.databinding.ActivityMainBinding;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TabPagerAdapter tabPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up ViewPager and TabLayout
        tabPagerAdapter = new TabPagerAdapter(this);
        binding.viewPager.setAdapter(tabPagerAdapter);

        // Connect TabLayout with ViewPager
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(tabPagerAdapter.getPageTitle(position))
        ).attach();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources if needed
    }
}