package com.stone.firstpage;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;

/**
 * 按照初叶的使用习惯，每一页应该是一个模板，所以此处命名时使用了Template关键字
 * 
 * @author Sistone.zhang
 * 
 */
@SuppressLint("HandlerLeak")
public class Template1Fragment extends Fragment {

	private Handler handler;
	private ImageSlidePanel slidePanel;

	private View leftShake, rightShake, bottomShake;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.template1_layout, null);
		slidePanel = (ImageSlidePanel) rootView
				.findViewById(R.id.image_slide_panel);

		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				slidePanel.startInAnim();
			}
		};

		leftShake = rootView.findViewById(R.id.left_shake);
		rightShake = rootView.findViewById(R.id.right_shake);
		bottomShake = rootView.findViewById(R.id.bottom_shake);

		Animation animationLeft = AnimationUtils.loadAnimation(getActivity(),
				R.anim.left_shake);
		Animation animationRight = AnimationUtils.loadAnimation(getActivity(),
				R.anim.right_shake);
		Animation animationBottom = AnimationUtils.loadAnimation(getActivity(),
				R.anim.bottom_shake);

		animationLeft.setInterpolator(new OvershootInterpolator(3));
		animationRight.setInterpolator(new OvershootInterpolator(3));

		leftShake.startAnimation(animationLeft);
		rightShake.startAnimation(animationRight);
		bottomShake.startAnimation(animationBottom);

		delayShowSlidePanel();
		return rootView;
	}

	private void delayShowSlidePanel() {

		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				handler.sendEmptyMessage(1);
			}
		}, 600);

	}
}
