package io.github.matveytomilov.pzblackbox;
public class TestTarget {
    public int work(int value){if(value<0)throw new IllegalArgumentException("synthetic_target_failure");return value+7;}
}
