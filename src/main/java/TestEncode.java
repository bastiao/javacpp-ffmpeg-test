
import org.bytedeco.javacpp.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.av_register_all;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.avutil.av_image_alloc;
import static org.bytedeco.javacpp.swscale.SWS_BILINEAR;
import static org.bytedeco.javacpp.swscale.sws_getCachedContext;
import static org.bytedeco.javacpp.swscale.sws_scale;

/**
 * Created by bastiao on 23-06-2016.
 */
public class TestEncode {

    private static avformat.AVFormatContext pFormatCtx = new avformat.AVFormatContext(null);

    private static avcodec.AVCodecContext c = null;
    private static avcodec.AVCodec pCodec = null;
    private static avutil.AVFrame frame = null;
    private static avutil.AVFrame pFrameRGB = null;
    private static avcodec.AVPacket pkt = new avcodec.AVPacket();
    private static int[] frameFinished = new int[1];
    private static int numBytes;
    private static BytePointer buffer = null;

    private static avutil.AVDictionary optionsDict = null;
    private static swscale.SwsContext sws_ctx = null;
    private static OutputStream stream = null;


    static void ffmpeg_encoder_set_frame_yuv_from_rgb(byte[] rgb) {


        int[] in_linesize = new int[1];
        in_linesize[0] = 3 * c.width();
        IntPointer ipt = new IntPointer(in_linesize);

        sws_ctx = sws_getCachedContext(sws_ctx, c.width(), c.height(), AV_PIX_FMT_RGB24,
                c.width(), c.height(), AV_PIX_FMT_YUV420P, SWS_BILINEAR,
                null, null, (DoublePointer) null);

        PointerPointer p = new PointerPointer(rgb);
        sws_scale(sws_ctx, p, ipt, 0,
                c.height(), frame.data(), frame.linesize());
    }

    public static byte[] generate_rgb(int width, int height, int pts) {
        int x, y, cur;
        byte[] rgb = new byte[3 * height * width];
        for (y = 0; y < height; y++) {
            for (x = 0; x < width; x++) {
                cur = 3 * (y * width + x);
                rgb[cur + 0] = 0;
                rgb[cur + 1] = 0;
                rgb[cur + 2] = 0;
                if ((frame.pts() / 25) % 2 == 0) {
                    if (y < height / 2) {
                        if (x < width / 2) {
                        /* Black. */
                        } else {
                            rgb[cur + 0] = (byte) 255;
                        }
                    } else {
                        if (x < width / 2) {
                            rgb[cur + 1] = (byte) 255;
                        } else {
                            rgb[cur + 2] = (byte) 255;
                        }
                    }
                } else {
                    if (y < height / 2) {
                        rgb[cur + 0] = (byte) 255;
                        if (x < width / 2) {
                            rgb[cur + 1] = (byte) 255;
                        } else {
                            rgb[cur + 2] = (byte) 255;
                        }
                    } else {
                        if (x < width / 2) {
                            rgb[cur + 1] = (byte) 255;
                            rgb[cur + 2] = (byte) 255;
                        } else {
                            rgb[cur + 0] = (byte) 255;
                            rgb[cur + 1] = (byte) 255;
                            rgb[cur + 2] = (byte) 255;
                        }
                    }
                }
            }
        }

        return rgb;
    }

    /* Allocate resources and write header data to the output file. */
    public static void ffmpeg_encoder_start(File f, int codec_id, int fps, int width, int height) {
        AVCodec codec = null;
        int ret;

        av_register_all();
        codec = avcodec_find_encoder(codec_id);
        if (codec == null) {
            System.err.println("No codec found_.");
        }
        c = avcodec_alloc_context3(codec);
        if (c == null) {
            System.err.println("_Could not allocate video codec context");

        }

        c.bit_rate(400000);
        c.width(width);
        c.height(height);
        avutil.AVRational av = new avutil.AVRational();
        av.num(1);
        av.den(fps);
        c.time_base(av);
        c.gop_size(10);
        c.max_b_frames(1);
        System.err.println("1");
        c.pix_fmt(AV_PIX_FMT_YUV420P);
        System.out.println("Codec: " + avcodec_open2(c, codec, new avutil.AVDictionary(null)));


        if (codec_id == AV_CODEC_ID_H264)
            av_opt_set(c.priv_data(), "preset", "slow", 0);
        if (avcodec_open2(c, codec, new AVDictionary(null)) < 0) {
            System.out.println("problem with codec open");
        }

        try {
            stream = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        frame = av_frame_alloc();
        if (frame == null) {
            System.out.println("Could not allocate frame");
        }

        frame.format(c.pix_fmt());
        frame.width(c.width());
        frame.height(c.height());

        ret = av_image_alloc(frame.data(),
                frame.linesize(), c.width(),
                c.height(),
                c.pix_fmt(), 32);

    }

    /*
    Write trailing data to the output file
    and free resources allocated by ffmpeg_encoder_start.
    */
    public static void ffmpeg_encoder_finish() {
        byte[] endcode = new byte[4];
        endcode[0] = 0;
        endcode[1] = 0;
        endcode[2] = 1;
        endcode[3] = (byte) 0xb7;
        int[] got_output = new int[1];
        got_output[0] = 0;
        int ret;
        do {

            ret = avcodec_encode_video2(c, pkt, new AVFrame(null), got_output);
            System.out.println("Ret: " + ret);
            if (ret < 0) {

                System.out.println("Error encoding frame\n");
                System.exit(1);

            }

            if (got_output[0] == 1) {
                //fwrite(pkt.data, 1, pkt.size, file);
                BytePointer data = pkt.data();
                byte[] bytes = new byte[1];
                for (int y = 0; y < pkt.size(); y++) {
                    data.position(y).get(bytes);
                    try {
                        stream.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //_stream.write(bytes);
                }
                av_packet_unref(pkt);
            }
        } while ((got_output[0] == 1));


        System.out.println("Stream 1");
        try {
            stream.write(endcode);
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Stream 2");
        avcodec_close(c);
        System.out.println("Stream 3");
        av_free(c);
        System.out.println("Stream 4");
        //av_freep(frame.data(0));
        //System.out.println("Stream 5" );
        //avcodec_free_frame(frame);
        //System.out.println("Stream 6" );

    }


    /*
    Encode one frame from an RGB24 input and save it to the output file.
    Must be called after ffmpeg_encoder_start, and ffmpeg_encoder_finish
    must be called after the last call to this function.
    */
    public static void ffmpeg_encoder_encode_frame(byte[] rgb) throws IOException {
        int ret;
        ffmpeg_encoder_set_frame_yuv_from_rgb(rgb);
        av_init_packet(pkt);
        pkt.size(0);
        pkt.data(null);
        int[] got_output = new int[1];
        got_output[0] = 0;
        ret = avcodec_encode_video2(c, pkt, frame, got_output);
        if (ret < 0) {
            System.out.println("Error encoding frame\n");
            System.exit(1);
        }
        if (got_output[0] == 1) {
            //fwrite(pkt.data, 1, pkt.size, file);
            BytePointer data = pkt.data();
            byte[] bytes = new byte[1];
            for (int y = 0; y < pkt.size(); y++) {
                data.position(y).get(bytes);
                try {
                    stream.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //_stream.write(bytes);
            }
            av_packet_unref(pkt);
        }

    }


    /* Represents the main loop of an application which generates one frame per loop. */
    public static void encode_example(String filename, int codec_id) {
        int pts;
        int width = 532;
        int height = 434;
        byte[] rgb;

        ffmpeg_encoder_start(new File(filename), codec_id, 25, width, height);
        System.out.println("ffmpeg_encoder_started\n");
        for (pts = 0; pts < 14; pts++) {
            frame.pts(pts);

            rgb = generate_rgb(width, height, pts);
            System.out.println("Rgb " + pts);
            try {
                ffmpeg_encoder_encode_frame(rgb);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ffmpeg_encoder_finish();
    }


    public static void main(String[] args) {
        av_register_all();

        //encode_example("tmp.h264", AV_CODEC_ID_H264);
        encode_example("/tmp/tmp.mpg", AV_CODEC_ID_MPEG1VIDEO);
        encode_example("/tmp/1.mpg", AV_CODEC_ID_MPEG1VIDEO);


    }
}
