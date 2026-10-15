package io.github.matveytomilov.pzblackbox.testpatch;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="io.github.matveytomilov.pzblackbox.TestTarget",methodName="protectedCallVoid")
public class SyntheticProtectedPatch {
    public static int arrays,scalars;
    @Patch.OnEnter public static void enter(@Patch.AllArguments Object[] args,@net.bytebuddy.asm.Advice.Origin("#d") String descriptor){if(descriptor.endsWith(";[Ljava/lang/Object;)V"))arrays++;else scalars++;}
}
