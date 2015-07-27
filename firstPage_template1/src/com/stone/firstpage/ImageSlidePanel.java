package com.stone.firstpage;

import java.util.ArrayList;
import java.util.List;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 这只是一个容器，只是处理了child的左右滑动而已
 * 
 */
@SuppressLint({ "HandlerLeak", "NewApi" })
public class ImageSlidePanel extends FrameLayout {
	private List<TextView> viewList = new ArrayList<TextView>();
	private TextView lastView; // 压在最底部的那个view
	private Handler uiHandler;

	/* 拖拽工具类 */
	private final ViewDragHelper mDragHelper;
	private int initCenterViewX = 0; // 最初时，中间View的x位置
	private int screenWidth = 0; // 屏幕宽度的一半

	private int rotateDegreeStep = 5; // view叠放时rotation旋转的step步长
	private int rotateAnimTime = 100; // 单个view旋转动画的时间

	private static final int MSG_TYPE_IN_ANIM = 1; // 进入时初始化动画类型
	private static final int MSG_TYPE_ROTATION = 2; // 左右滑动消失后，各个view调整rotation
	private static final int XVEL_THRESHOLD = 100; // 滑动速度超过这个值，则直接被认定为向两边飞走消失
	private boolean isRotating = false;

	public ImageSlidePanel(Context context) {
		this(context, null);
	}

	public ImageSlidePanel(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	@SuppressWarnings("deprecation")
	public ImageSlidePanel(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		// 获取屏幕宽度
		WindowManager wm = (WindowManager) getContext().getSystemService(
				Context.WINDOW_SERVICE);
		screenWidth = wm.getDefaultDisplay().getWidth();

		// 定义handler专门负责线程交互
		uiHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				Bundle data = msg.getData();
				int cycleNum = data.getInt("cycleNum");
				if (msg.what == MSG_TYPE_IN_ANIM) {
					processInAnim(cycleNum);
				} else if (msg.what == MSG_TYPE_ROTATION) {
					processRotaitonAnim(cycleNum);
				}
			}
		};

		// 滑动相关类
		mDragHelper = ViewDragHelper
				.create(this, 10f, new DragHelperCallback());
		mDragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT);
	}

	@Override
	protected void onFinishInflate() {
		// 渲染成功之后，将需要处理的childView保存起来
		initViewList();
	}

	class XScrollDetector extends SimpleOnGestureListener {
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx,
				float dy) {
			return Math.abs(dy) > Math.abs(dx);
		}
	}

	/**
	 * 初始化framelayout里面的view链表
	 */
	private void initViewList() {
		viewList.clear();
		int num = getChildCount();
		for (int i = 0; i < num; i++) {
			TextView tv = (TextView) getChildAt(i);
			tv.setRotation((num - 1 - i) * rotateDegreeStep);
			viewList.add(tv);
		}

		lastView = viewList.get(viewList.size() - 1);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		initCenterViewX = lastView.getLeft();
	}

	/**
	 * 这是文件夹拖拽效果的主要逻辑
	 */
	private class DragHelperCallback extends ViewDragHelper.Callback {

		@Override
		public void onViewPositionChanged(View changedView, int left, int top,
				int dx, int dy) {
			// 判断是否已经在边缘状态
			// 如果是边缘状态，则需要重新叠放view并启动动画
			if (left == -lastView.getWidth() || left == screenWidth) {

				// onViewPositionChanged可能会多次调用，因为offsetLeftAndRight函数会触发view位置变动
				// 此处加上一个flag来抛弃那些没有必要的view叠放更新
				if (!isRotating) {
					// 锁定isRotating
					isRotating = true;
					// abort一下，否则会粗现混乱
					mDragHelper.abort();

					// 下面要计算lastView要移动的offsetLeftAndRight
					// viewDragHelper就是通过这个offsetLeftAndRight实施动画的，F他大爷
					int offsetLeftAndRight;
					if (left < 0) {
						offsetLeftAndRight = Math.abs(left) + initCenterViewX;
					} else {
						offsetLeftAndRight = initCenterViewX - left;
					}

					// 调用这个函数会导致下一次继续调用到onViewPositionChanged()
					lastView.offsetLeftAndRight(offsetLeftAndRight);
					orderViewStack();
				}
			} else if (!isRotating && changedView.getRotation() == 0) {
				processAlphaGradual(changedView, left);
			}
		}

		@Override
		public boolean tryCaptureView(View child, int pointerId) {
			// 只捕获顶部view(rotation=0)
			if (child == lastView) {
				return true;
			}
			return false;
		}

		@Override
		public int getViewHorizontalDragRange(View child) {
			// 这个用来控制拖拽过程中松手后，自动滑行的速度
			return 256;
		}

		@Override
		public void onViewReleased(View releasedChild, float xvel, float yvel) {
			animToFade(xvel);
		}

		@Override
		public int clampViewPositionHorizontal(View child, int left, int dx) {
			return left;
		}

		@Override
		public int clampViewPositionVertical(View child, int top, int dy) {
			return child.getTop();
		}
	}

	/**
	 * 对View重新排序
	 */
	private void orderViewStack() {
		// 1. viewList中的view在framelayout顺次调整
		int num = viewList.size();
		for (int i = 0; i < num - 1; i++) {
			TextView tempView = viewList.get(i);
			tempView.bringToFront();
		}
		invalidate();

		// 2. lastView交接
		lastView.setAlpha(1);
		lastView.setRotation((viewList.size() - 1) * rotateDegreeStep);
		viewList.remove(lastView);
		viewList.add(0, lastView);
		lastView = viewList.get(viewList.size() - 1);

		// 3. 启动动画旋转的线程
		new MyThread(MSG_TYPE_ROTATION, viewList.size(), rotateAnimTime).start();
	}

	/**
	 * 松手时处理滑动到边缘的动画
	 * 
	 * @param xvel
	 *            X方向上的滑动速度
	 */
	private void animToFade(float xvel) {
		// 这个是滑动消失的x目标位置
		// 以下做了一坨的事情主要来计算这个finalLeft
		int finalLeft = initCenterViewX;

		if (xvel > XVEL_THRESHOLD) {
			// x正方向速度大于XVEL_THRESHOLD时，则直接向右飞走消失
			finalLeft = screenWidth;
		} else if (xvel < -XVEL_THRESHOLD) {
			// x负方向速度大于XVEL_THRESHOLD时，则直接向左飞走消失
			finalLeft = -lastView.getWidth();
		} else {
			// 根据是否跨越了中间线，来判断是否两边消失
			if (lastView.getLeft() > screenWidth / 2) {
				finalLeft = screenWidth;
			} else if (lastView.getRight() < screenWidth / 2) {
				finalLeft = -lastView.getWidth();
			}
		}

		if (mDragHelper.smoothSlideViewTo(lastView, finalLeft,
				lastView.getTop())) {
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}
	
	
	/**
	 * 点击了按钮想左右扩展
	 * @param type -1向左 0居中 1向右
	 */
	public void onClickFade(int type) {
		int finalLeft = 0;
		if (type == -1) {
			finalLeft = -lastView.getWidth();
		}
		else if (type == 1) {
			finalLeft = screenWidth;
		}
		
		if (finalLeft != 0) {
			if (mDragHelper.smoothSlideViewTo(lastView, finalLeft,
					lastView.getTop())) {
				ViewCompat.postInvalidateOnAnimation(this);
			}
		}
	}

	@Override
	public void computeScroll() {
		if (mDragHelper.continueSettling(true)) {
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	// 滑动的时候处理aplha渐变
	private void processAlphaGradual(View changedView, int left) {
		float alpha = 1.0f;
		int halfScreenWidth = screenWidth / 2;
		if (left > initCenterViewX) {
			// 向右滑动
			if (left > halfScreenWidth) {
				// 向右越过了中间线
				alpha = ((float) left - halfScreenWidth) / halfScreenWidth;
				alpha = 1 - alpha;
			}
		} else if (left < initCenterViewX) {
			// 向左滑动
			if (changedView.getRight() < halfScreenWidth) {
				// 向左越过了中间线
				alpha = ((float) halfScreenWidth - changedView.getRight())
						/ halfScreenWidth;
				alpha = 1 - alpha;
			}
		}

		changedView.setAlpha(alpha);
	}

	/* touch事件的拦截与处理都交给mDraghelper来处理 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		boolean shouldIntercept = mDragHelper.shouldInterceptTouchEvent(ev);
		int action = ev.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			// 保存初次按下时arrowFlagView的Y坐标
			// action_down时就让mDragHelper开始工作，否则有时候导致异常
			mDragHelper.processTouchEvent(ev);
		}

		return shouldIntercept;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		// 统一交给mDragHelper处理，由DragHelperCallback实现拖动效果
		mDragHelper.processTouchEvent(e); // 该行代码可能会抛异常，正式发布时请将这行代码加上try catch
		return true;
	}

	/**
	 * 处理的是初始化image飞入动画
	 */
	private void processInAnim(int cycleNum) {
		Animation animation = AnimationUtils.loadAnimation(getContext(),
				R.anim.image_in);

		Interpolator interpolator = new OvershootInterpolator(0.8f);
		animation.setInterpolator(interpolator);
		View view = viewList.get(cycleNum);
		view.setVisibility(View.VISIBLE);
		view.startAnimation(animation);
	}

	/**
	 * 处理rotation旋转,使用属性动画
	 */
	private void processRotaitonAnim(int cycleNum) {
		if (cycleNum >= viewList.size() - 1) {
			// 最底部的View被盖住了，无需动画，释放isRotating flag
			isRotating = false;
			return;
		}

		// 使用属性动画旋转gradualDegreeStep角度
		TextView tv = viewList.get(viewList.size() - 1 - cycleNum);
		float fromDegree = tv.getRotation();
		ObjectAnimator animator = ObjectAnimator
				.ofFloat(tv, "rotation", fromDegree,
						fromDegree - rotateDegreeStep)
				.setDuration(rotateAnimTime * 3);
		animator.start();
	}

	/**
	 * 启动飞入动画
	 */
	public void startInAnim() {
		new MyThread(MSG_TYPE_IN_ANIM, viewList.size(), 100).start();
	}

	/**
	 * 这个是专门处理飞入动画的线程
	 * 
	 */
	class MyThread extends Thread {
		private int num; // 循环次数
		private int type; // 事件类型
		private int sleepTime; // sleep的时间

		public MyThread(int type, int num, int sleepTime) {
			this.type = type;
			this.num = num;
			this.sleepTime = sleepTime;
		}

		@Override
		public void run() {
			for (int i = 0; i < num; i++) {
				
				Message msg = uiHandler.obtainMessage();
				msg.what = type;
				Bundle data = new Bundle();
				data.putInt("cycleNum", i);
				msg.setData(data);
				msg.sendToTarget();
				
				try {
					sleep(sleepTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}
	}
}
