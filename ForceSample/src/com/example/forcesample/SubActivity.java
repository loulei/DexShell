package com.example.forcesample;

import android.app.Activity;
import android.os.Bundle;

public class SubActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
//		int layoutId = getResources().getIdentifier("activity_sub", "layout", "com.example.pluginactivity");
		setContentView(R.layout.activity_sub);
	}
}
