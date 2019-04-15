package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.NumberPicker;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArraySet;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;

    Double[] freqs;

    public Listentone(){

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);

        mAudioRecord.startRecording();
        //AudioFormat.ENCODING_PCM_

    }


    public void PreRequest() {
        // 패킷들을 쌓아둘 버퍼 생성
        Queue<Integer> packetList = new LinkedList();
        // 패킷이 들어오긴 했는지 판별
        boolean in_packet = false;

        int blocksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        short[] buffer = new short[blocksize];

        freqs = this.fftfreq(blocksize, 1);
        // 소리를 계속 듣자
        while(true) {
            // 소리 듣기, buffer에 데이터 저장
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
            // 리턴값 음수면 에러
            if (bufferedReadResult<0)
                continue;

            // 주파수 계산을 위해 double로 변환
            double[] buffer_double = new double[blocksize];
            for(int i=0; i<blocksize; i++) {
                buffer_double[i] = buffer[i];
            }
            // 주파수 계산
            int dominant = (int)findFrequency(buffer_double);
            // Log.d("dominant ", ""+dominant);

            if(in_packet && match(dominant, HANDSHAKE_END_HZ)) {
                // 패킷 수집 종료

                // 해석
                char[] byte_stream = extract_packet(packetList);
                // 로그에 출력
                Log.e("decoded : ", String.valueOf(byte_stream));

                packetList.clear();
                in_packet = false;
            } else if (in_packet) {
                // 패킷 수집
                packetList.add(dominant);
            } else if(match(dominant, HANDSHAKE_START_HZ)) {
                // 패킷 수집 시작
                in_packet = true;
            }
        }
    }
    private int findPowerSize(int dataSize) {
        // 가까운 2의 제곱수 계산
        int log2 = (int) (Math.log10(dataSize)/Math.log10(2));
        int pow1 = (int)Math.pow(2, log2);
        int pow2 = pow1*2;

        // 가까운 2의 제곱수 중, 더 가까운 수 리턴
        if(Math.abs(dataSize-pow1) < Math.abs(dataSize-pow2))
            return pow1;
        else return pow2;
    }

    private double findFrequency(double[] toTransform) {
        int len = toTransform.length;
        double[] real = new double[len]; // 실수부 배열
        double[] img = new double[len]; // 허수부 배열
        double realNum; // 복소수의 실수부
        double imgNum; // 복소수의 허수부

        // 푸리에 변환, 변환결과는 복소수들
        Complex[] complexs = transform.transform(toTransform, TransformType.FORWARD);

        // 푸리에 변환 결과들 중, 절댓값 최대치 인덱스 찾기
           // 절대값 최대치 인덱스
        int maxIndex=0;
           // 절대값 최대치
        double maxValue= Double.MIN_VALUE;
           // 검색
        for(int i=0; i<complexs.length; i++) {
            if(complexs[i].abs() > maxValue) {
                maxValue = complexs[i].abs();
                maxIndex = i;
            }
        }

        // 뽑아오기
        double peek_freq = freqs[maxIndex].doubleValue();

        // 리턴
        return Math.abs(peek_freq * mSampleRate);

    }

    private Double[] fftfreq(int length, double duration) {
        Double[] freqs = new Double[length];
        int mid = length/2;
        double commonDevide = 1.0/(duration*length);

        // 0~중간
        for(int i=0; i<mid; i++) {
            freqs[i] = i*commonDevide;
        }
        // 중간~끝
        for(int i=mid; i<length; i++) {
            freqs[i] = -((length-i)*commonDevide);
        }

        // in our case, [0 ~ 0.49999, -0.49999 ~ 0]
        return freqs;
    }

    private boolean match(double freq1, double freq2) {
        return Math.abs(freq1-freq2)<20;
    }

    // 패킷(주파수) 들을 문자로 변환
    private char[] extract_packet(Queue<Integer> freqs) {
        // 첫 데이터는 시작을 알리는 주파수. 버림
        freqs.poll();

        // 청크(0~15의 스텝)들을 저장할 리스트
        // 모든 주파수는 두번씩 들어온다고 생각.
        ArrayList<Byte> list = new ArrayList<>();

        while(!freqs.isEmpty()) {
            double f = freqs.poll();

            // 스텝 계산
            long step = Math.round((f-START_HZ)/STEP_HZ);
            // 유효한 범위의 스텝이면 추가
            if(step >=0 && step< Math.pow(2, BITS)) {
                list.add((byte)step);
            }

            // 주파수당 2개씩 들어오니, 하나는 버림
            if(!freqs.isEmpty())
                freqs.poll();
        }

        // 리스트를 배열로 변환
        Byte[] bit_chunks = list.toArray(new Byte[list.size()]);

        // 해독된 문자들. char배열 리턴.
        // 주파수 2개당 문자 1개
        return decode_bitchunks(bit_chunks);
    }

    // 청크(0~15의 스텝)들을 문자로 변환
    private char[] decode_bitchunks(Byte[] bit_chunks) {
        /* debug
        for(int i=0; i<bit_chunks.length; i++) {
            Log.e("step "+(i+1), ""+bit_chunks[i]);
        }
        */

        // 몇개 주파수가 하나 문자 만드는지, 이 케이스에선 주파수 2개당 문자 하나
        byte data_per_char = (byte)(8/BITS);

        // 주파수 2개당 문자 1개
        char[] decoded_char = new char[bit_chunks.length / data_per_char];

        int bit_chunk_p=-1; // bit_chunks를 탐색할 포인터

        for(int p=0; p < decoded_char.length; p++) {
            // 문자 하나씩 생성
            int combined = 0;

            // 2개의 주파수로 1개 문자 만들기.
            for(byte i=0; i<data_per_char; i++) {
                combined = combined << BITS; // 왼쪽으로 밀기
                combined += bit_chunks[++bit_chunk_p];
            }
            decoded_char[p] = (char)combined;
        }

        /* debug
        for(int i=0; i<decoded_char.length; i++)
            Log.e("decoded char "+(i+1), ""+decoded_char[i]);
        */

        return decoded_char;
    }
}
