package fitme.ai;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created by 69441 on 2017/5/27.
 */

public class ShellUtils {

    private static final String COMMAND_SU = "su";
    private static final String COMMAND_SH = "sh";
    private static final String COMMAND_EXIT = "exit\n";
    private static final String COMMAND_LINE_END = "\n";

    public static CommandResult execCommands(String commands, boolean isRoot) {
        return execCommands(new String[] {commands}, isRoot, true);
    }

    public static CommandResult execCommands(List<String> commands, boolean isRoot) {
        return execCommands(commands == null ? null : commands.toArray(new String[] {}), isRoot, true);
    }

    public static CommandResult execCommands(String commands, boolean isRoot, boolean isNeedResult) {
        return execCommands(commands, isRoot, isNeedResult);
    }

    public static CommandResult execCommands(List<String> commands, boolean isRoot, boolean isNeedResult) {
        return execCommands(commands == null ? null : commands.toArray(new String[] {}), isRoot, isNeedResult);
    }

    public static CommandResult execCommands(String[] commands, boolean isRoot, boolean isNeedResult) {
        int result = -1;
        if (commands == null || commands.length == 0) {
            return new CommandResult(result, null, null);
        }

        Process mProcess = null;
        BufferedReader successResult = null;
        BufferedReader errorResult = null;
        StringBuilder successMsg = null;
        StringBuilder errorMsg = null;
        DataOutputStream dos = null;

        try {
            mProcess = Runtime.getRuntime().exec(isRoot ? COMMAND_SU : COMMAND_SH);
            dos = new DataOutputStream(mProcess.getOutputStream());
            for (String command : commands) {
                if (command == null) {
                    continue;
                }
                dos.write(command.getBytes());
                dos.writeBytes(COMMAND_LINE_END);
                dos.flush();
            }
            dos.writeBytes(COMMAND_EXIT);
            dos.flush();

            result = mProcess.waitFor();
            if (isNeedResult) {
                successMsg = new StringBuilder();
                errorMsg = new StringBuilder();
                successResult = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
                errorResult = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
                String s;
                while ((s = successResult.readLine()) != null) {
                    successMsg.append(s);
                }
                while (((s = errorResult.readLine()) != null)) {
                    errorMsg.append(s);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (dos != null) {
                    dos.close();
                }
                if (successResult != null) {
                    successResult.close();
                }
                if (errorResult != null) {
                    errorResult.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (mProcess != null) {
                mProcess.destroy();
            }
        }
        return new CommandResult(result, successMsg == null ? null : successMsg.toString(),
                errorMsg == null ? null : errorMsg.toString());

    }

    public static class CommandResult {
        public int result;
        public String successMsg;
        public String errorMsg;

        public CommandResult(int result) {
            this.result = result;
        }

        public CommandResult(int result, String successMsg, String errorMsg) {
            this.result = result;
            this.successMsg = successMsg;
            this.errorMsg = errorMsg;
        }
    }

}
