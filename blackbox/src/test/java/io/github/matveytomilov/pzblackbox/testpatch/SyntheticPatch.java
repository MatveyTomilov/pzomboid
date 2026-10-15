package io.github.matveytomilov.pzblackbox.testpatch;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="io.github.matveytomilov.pzblackbox.TestTarget",methodName="work")
public class SyntheticPatch {
    public static int entered,exited,thrown;
    @Patch.OnEnter public static void enter(@Patch.Argument(0) int value,@Patch.Local("bbSyntheticStart") long start){entered++;start=System.nanoTime();}
    @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Argument(0) int value,@Patch.Local("bbSyntheticStart") long start,@Patch.Thrown Throwable failure){exited++;if(failure!=null)thrown++;if(start<=0)throw new AssertionError("missing local");}
}
