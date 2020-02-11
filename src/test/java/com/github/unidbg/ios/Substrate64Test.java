package com.github.unidbg.ios;

import com.github.unidbg.Emulator;
import com.github.unidbg.LibraryResolver;
import com.github.unidbg.Module;
import com.github.unidbg.Symbol;
import com.github.unidbg.android.EmulatorTest;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.hook.ReplaceCallback;
import com.github.unidbg.hook.hookzz.HookEntryInfo;
import com.github.unidbg.hook.hookzz.HookZz;
import com.github.unidbg.hook.hookzz.IHookZz;
import com.github.unidbg.hook.hookzz.WrapCallback;
import com.github.unidbg.hook.whale.IWhale;
import com.github.unidbg.hook.whale.Whale;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnicornPointer;
import com.github.unidbg.utils.Inspector;
import com.sun.jna.Pointer;
import junit.framework.AssertionFailedError;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Arrays;

public class Substrate64Test extends EmulatorTest {

    @Override
    protected LibraryResolver createLibraryResolver() {
        return new DarwinResolver();
    }

    @Override
    protected Emulator createARMEmulator() {
        return new DarwinARM64Emulator();
    }

    public void testMS() throws Exception {
        MachOLoader loader = (MachOLoader) emulator.getMemory();
        loader.setCallInitFunction();
//        Debugger debugger = emulator.attach();
//        debugger.addBreakPoint(null, 0x100dd29b4L);
        Logger.getLogger("com.github.unidbg.AbstractEmulator").setLevel(Level.DEBUG);
//        emulator.traceCode();
        loader.setObjcRuntime(true);
        Module module = emulator.loadLibrary(new File("src/test/resources/example_binaries/libsubstrate.dylib"));

//        Logger.getLogger("com.github.emulator.ios.ARM32SyscallHandler").setLevel(Level.DEBUG);

        IWhale whale = Whale.getInstance(emulator);
        whale.WImportHookFunction("_malloc", new ReplaceCallback() {
            @Override
            public HookStatus onCall(Emulator emulator, long originFunction) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(0);
                System.err.println("IWhale hook _malloc size=" + size);
                return HookStatus.RET(emulator, originFunction);
            }
        });

        IHookZz hookZz = HookZz.getInstance(emulator);
        Symbol malloc_zone_malloc = module.findSymbolByName("_malloc_zone_malloc");
//        emulator.traceCode();
//        Logger.getLogger("com.github.unidbg.AbstractEmulator").setLevel(Level.DEBUG);
        hookZz.replace(malloc_zone_malloc, new ReplaceCallback() {
            @Override
            public HookStatus onCall(Emulator emulator, long originFunction) {
                RegisterContext context = emulator.getContext();
                Pointer zone = context.getPointerArg(0);
                int size = context.getIntArg(1);
                System.err.println("_malloc_zone_malloc zone=" + zone + ", size=" + size);
                return HookStatus.RET(emulator, originFunction);
            }
        });

        Symbol symbol = module.findSymbolByName("_MSGetImageByName");
        assertNotNull(symbol);

//        emulator.traceCode();
        hookZz.wrap(symbol, new WrapCallback<RegisterContext>() {
            @Override
            public void preCall(Emulator emulator, RegisterContext ctx, HookEntryInfo info) {
                System.err.println("preCall _MSGetImageByName=" + ctx.getPointerArg(0).getString(0));
            }
            @Override
            public void postCall(Emulator emulator, RegisterContext ctx, HookEntryInfo info) {
                super.postCall(emulator, ctx, info);
                System.err.println("postCall _MSGetImageByName ret=0x" + Long.toHexString(ctx.getLongArg(0)));
            }
        });

//        emulator.attach().addBreakPoint(null, 0x40235d2a);
//        emulator.traceCode();

        MemoryBlock memoryBlock = emulator.getMemory().malloc(0x40, false);
        UnicornPointer memory = memoryBlock.getPointer();
        Symbol _snprintf = module.findSymbolByName("_snprintf", true);
        assertNotNull(_snprintf);

        byte[] before = memory.getByteArray(0, 0x40);
        Inspector.inspect(before, "Before memory=" + memory);
//        emulator.traceCode();
//        emulator.traceWrite(memory.peer, memory.peer + 0x40);
//        emulator.traceWrite();
        String fmt = "Test snprintf=%p\n";
//        emulator.traceRead(0xbffff9b8L, 0xbffff9b8L + fmt.length() + 1);
//        emulator.attach().addBreakPoint(null, 0x401622c2);
        _snprintf.call(emulator, memory, 0x40, fmt, memory);
        byte[] after = memory.getByteArray(0, 0x40);
        Inspector.inspect(after, "After");
        if (Arrays.equals(before, after)) {
            throw new AssertionFailedError();
        }
//        emulator.attach().addBreakPoint(null, 0x40234c1e);
        memoryBlock.free(false);

        long start = System.currentTimeMillis();

//        emulator.traceRead();
//        emulator.attach().addBreakPoint(null, 0x401495dc);
//        emulator.traceCode();

        /*whale.WInlineHookFunction(symbol, new ReplaceCallback() {
            @Override
            public HookStatus onCall(Emulator emulator, long originFunction) {
                Unicorn unicorn = emulator.getUnicorn();
                Pointer pointer = UnicornPointer.register(emulator, ArmConst.UC_ARM_REG_R0);
                System.err.println("onCall _MSGetImageByName=" + pointer.getString(0) + ", origin=" + UnicornPointer.pointer(emulator, originFunction));
                return HookStatus.RET(unicorn, originFunction);
            }
        });*/

//        emulator.traceCode();
        whale.WImportHookFunction("_strcmp", new ReplaceCallback() {
            @Override
            public HookStatus onCall(Emulator emulator, long originFunction) {
                RegisterContext context = emulator.getContext();
                Pointer pointer1 = context.getPointerArg(0);
                Pointer pointer2 = context.getPointerArg(1);
                System.out.println("strcmp str1=" + pointer1.getString(0) + ", str2=" + pointer2.getString(0) + ", originFunction=0x" + Long.toHexString(originFunction));
                return HookStatus.RET(emulator, originFunction);
            }
        });

        // emulator.attach().addBreakPoint(module, 0x00b608L);
//        emulator.traceCode();
        Number[] numbers = symbol.call(emulator, "/Library/Frameworks/CydiaSubstrate.framework/CydiaSubstrate");
        long ret = numbers[0].intValue() & 0xffffffffL;
        System.err.println("_MSGetImageByName ret=0x" + Long.toHexString(ret) + ", offset=" + (System.currentTimeMillis() - start) + "ms");

        symbol = module.findSymbolByName("_MSFindSymbol");
        assertNotNull(symbol);
        start = System.currentTimeMillis();
        // emulator.traceCode();
        numbers = symbol.call(emulator, ret, "_MSGetImageByName");
        ret = numbers[0].intValue() & 0xffffffffL;
        System.err.println("_MSFindSymbol ret=0x" + Long.toHexString(ret) + ", offset=" + (System.currentTimeMillis() - start) + "ms");
    }

    public static void main(String[] args) throws Exception {
        Substrate64Test test = new Substrate64Test();
        test.setUp();
        test.testMS();
        test.tearDown();
    }

}