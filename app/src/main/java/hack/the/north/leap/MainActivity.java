package hack.the.north.leap;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SurfaceView;
import android.widget.ImageView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private boolean mIsColorSelected = false;
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar mBlobColorHsv;
    private HandDetection mDetector;
//    private Mat mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar CONTOUR_COLOR, HULL_COLOR, HULL_WARN_COLOR;

    private double mDeltaX = 0, mDeltaY = 0;

    private Point lastCenter;
    private boolean lastFist;

    private final static int CAMERA_PERMISSION_REQUEST = 1337;

    private int width, height;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ImageView mImageView;
    private ImageView nImageView;
    private int directionOldImage=Integer.MAX_VALUE;
    private int fistOldImage=Integer.MAX_VALUE;
    private void drawDirection(int direction) {
        if (directionOldImage != direction) {
            directionOldImage = direction;
            mImageView = (ImageView) findViewById(R.id.imageDirection);
            final Drawable draw;
            switch (direction) {
                case 0:
                    draw = null;
                    break;
                case 1:
                    draw = getDrawable(R.drawable.up_arrow);
                    break;
                case 2:
                    draw = getDrawable(R.drawable.right_arrow);
                    break;
                case 3:
                    draw = getDrawable(R.drawable.down_arrow);
                    break;
                case 4:
                    draw = getDrawable(R.drawable.left_arrow);
                    break;
                default:
                    draw = null;
                    break;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mImageView.setBackground(draw);
                    /*
                    try {
                        Thread.sleep(800);
                    } catch (Exception e) {
                        //kek
                    }
                    mImageView.setImageBitmap(null);
                    */
                }
            });
        }
    }
    private void drawFist(int direction){
        if (fistOldImage!=direction) {
            fistOldImage = direction;
            nImageView = (ImageView) findViewById(R.id.imageFist);
            final Drawable draw;
            switch (direction) {
                case 0:
                    draw = getDrawable(R.drawable.fist);
                    break;
                case 1:
                    draw = getDrawable(R.drawable.unfist);
                    break;
                default:
                    draw = null;
                    break;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    nImageView.setBackground(draw);
                    /*
                    try {
                        Thread.sleep(800);
                    } catch (Exception e) {
                        //kek
                    }
                    nImageView.setImageBitmap(null);
                    */
                }
            });
        }

    }
    private void checkHandDirection(Point location){
        int cX = (int)location.x;
        int cY = (int)location.y;

        int rightXQuart = width/4;
        int leftXQuart = (3*width)/4;
        int topYQuart = height/4;
        int bottomYQuart = (3*height)/4;

        if (cX < rightXQuart){//right
            drawDirection(2);
            setDirection(2);
        }else if (cX > leftXQuart){//left
            drawDirection(4);
            setDirection(4);
        }else if (cY < topYQuart){//up
            drawDirection(1);
            setDirection(1);
        }else if (cY > bottomYQuart){//down
            drawDirection(3);
            setDirection(3);
        }else{
            drawDirection(0);
            setDirection(0);
        }
    }

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(1);

        Display display = getWindowManager().getDefaultDisplay();
        android.graphics.Point size = new android.graphics.Point();
        display.getSize(size);
        width = 1920;
        height = 1080;

        lastFist = false;

        requestCameraPermission();

    }

    public void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        CAMERA_PERMISSION_REQUEST);
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {

                }
                return;
            }

        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new HandDetection();
//        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
//        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        HULL_COLOR = new Scalar(0,255,0,255);
        HULL_WARN_COLOR = new Scalar(255,168,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        //Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (mIsColorSelected) {
            mDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());


            if (!contours.isEmpty()) {
                MatOfInt hullIndex = new MatOfInt();

                MatOfPoint largest;

                if (contours.size() == 1) {
                    largest = contours.get(0);
                } else {
                    double max = Imgproc.contourArea(contours.get(0));
                    int index = 0;
                    for (int i = 1; i < contours.size(); i++) {
                        double val = Imgproc.contourArea(contours.get(i));
                        if (val > max) {
                            max = val;
                            index = i;
                        }
                    }
                    largest = contours.get(index);
                }
                Imgproc.convexHull(largest, hullIndex);
                List<Integer> hullList = hullIndex.toList();

                List<Point> contourPoints = largest.toList();
                List<Point> hullPoints = new ArrayList<>();

                for (int i : hullList) {
                    hullPoints.add(contourPoints.get(i));
                }

                MatOfPoint hullMat = new MatOfPoint();
                hullMat.fromList(hullPoints);

                List<MatOfPoint> hulls = new ArrayList<>();
                hulls.add(hullMat);

                Point hullCenter = getCenterPoint(hullPoints);
                Point contourCenter = getCenterPoint(contourPoints);
                checkHandDirection(contourCenter);

                boolean hullIsOnEdge = edgeScan(hullPoints, 1912, 1072);

                Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR, 5);
                Imgproc.circle(mRgba, contourCenter, 10, CONTOUR_COLOR, -1);

                double hullArea = Imgproc.contourArea(hullMat);
                setHullArea(hullArea);

                double contourArea = Imgproc.contourArea(largest);
                double solidity = contourArea/hullArea;

                if (lastCenter != null) {
                    mDeltaX = contourCenter.x - lastCenter.x;
                    mDeltaY = contourCenter.y - lastCenter.y;
                    setDelta(mDeltaX, mDeltaY);
                }

                lastCenter = hullCenter;

                if (solidity > 0.82f && !hullIsOnEdge) {
                    if (! lastFist) {
                        if (!isMovement()) {
                            setFist(true);
                            lastFist = true;
                            drawFist(0);
                        } else {
                            drawFist(1);
                        }
                    }
                } else {
                    if (hullIsOnEdge) {
                        drawFist(-1);
                    } else {
                        drawFist(1);
                    }
                    if (lastFist) {
                        setFist(false);
                        lastFist = false;
                    }
                }

                if (!hullIsOnEdge) {
                    Imgproc.drawContours(mRgba, hulls, -1, HULL_COLOR, 5);
                    Imgproc.circle(mRgba, hullCenter, 10, HULL_COLOR, -1);
                } else {
                    Imgproc.drawContours(mRgba, hulls, -1, HULL_WARN_COLOR, 7);
                    Imgproc.circle(mRgba, hullCenter, 10, HULL_WARN_COLOR, -1);
                }
            } else {
                drawDirection(0);
                drawFist(Integer.MAX_VALUE);
            }



            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);
        }


        Core.flip(mRgba, mRgba, 1);

        return mRgba;
    }

    private boolean isMovement() {
        return (Math.abs(mDeltaX) > 100 || Math.abs(mDeltaY) > 100);
    }

    private Point getCenterPoint(List<Point> points) {
        double sumX = 0;
        double sumY = 0;

        for (int i=0; i < points.size(); i++) {
            Point point = points.get(i);
            sumX += point.x;
            sumY += point.y;
        }

        double cX = sumX / points.size();
        double cY = sumY / points.size();

        return new Point(cX, cY);
    }
    public void setDirection(int d){
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("values/direction");
        ref.setValue(d);
    }
    public void setFist(boolean b) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("values/fist");
        ref.setValue(b);
    }

    public void setDelta(double x, double y) {
        DatabaseReference refx = FirebaseDatabase.getInstance().getReference("values/deltaX");
        DatabaseReference refy = FirebaseDatabase.getInstance().getReference("values/deltaY");

        refx.setValue(x);
        refy.setValue(y);
    }

    public void setHullArea(double a) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("values/hullArea");
        ref.setValue(a);
    }

    public boolean edgeScan(List<Point> points, int width, int height) {
        for (Point p : points) {
            Log.e(TAG, p.x + " " + p.y);
            if (p.x <= 8 || p.x >= width-4 || p.y <= 8 || p.y >= height-4){
                return true;
            }
        }
        return false;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}

