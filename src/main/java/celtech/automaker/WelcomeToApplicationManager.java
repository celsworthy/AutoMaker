package celtech.automaker;

import celtech.Lookup;
import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import celtech.configuration.ApplicationConfiguration;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Ian
 */
class WelcomeToApplicationManager
{
    static void displayWelcomeIfRequired()
    {
        if (applicationJustInstalled())
        {
            showWelcomePage();
            String localVersionFilename = ApplicationConfiguration.getApplicationName()
                + "lastRunVersion";
            try
            {
                FileUtils.writeStringToFile(new File(ApplicationConfiguration.
                    getApplicationStorageDirectory() + localVersionFilename), ApplicationConfiguration.getApplicationVersion(), "US-ASCII");
            } catch (IOException ex)
            {
                System.err.println("Failed to write last run version");
            }
        }
    }

    private static boolean applicationJustInstalled()
    {
        boolean needToDisplayWelcome = false;
        String localVersionFilename = ApplicationConfiguration.getApplicationName()
            + "lastRunVersion";

        try
        {
            String lastRunVersion = FileUtils.readFileToString(new File(ApplicationConfiguration.
                getApplicationStorageDirectory() + localVersionFilename), "US-ASCII");

            if (!lastRunVersion.equalsIgnoreCase(ApplicationConfiguration.getApplicationVersion()))
            {
                needToDisplayWelcome = true;
            }
        } catch (IOException ex)
        {
            //The file did not exist
            needToDisplayWelcome = true;
        }

        return needToDisplayWelcome;
    }

    private static void showWelcomePage()
    {
        Lookup.getTaskExecutor().runOnGUIThread(() ->
        {
            ApplicationStatus.getInstance().setMode(ApplicationMode.WELCOME);
        });
    }
}
