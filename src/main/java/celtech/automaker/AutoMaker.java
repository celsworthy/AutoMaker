package celtech.automaker;

import celtech.Lookup;
import celtech.appManager.ApplicationMode;
import celtech.appManager.TaskController;
import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.DisplayManager;
import celtech.printerControl.PrinterStatus;
import celtech.printerControl.comms.RoboxCommsManager;
import celtech.printerControl.model.Printer;
import celtech.printerControl.model.PrinterException;
import celtech.utils.AutoUpdate;
import celtech.utils.AutoUpdateCompletionListener;
import static celtech.utils.SystemValidation.check3DSupported;
import static celtech.utils.SystemValidation.checkMachineTypeRecognised;
import celtech.utils.tasks.TaskResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import libertysystems.configuration.ConfigNotLoadedException;
import libertysystems.configuration.Configuration;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class AutoMaker extends Application implements AutoUpdateCompletionListener
{
    
    private static final Stenographer steno = StenographerFactory.getStenographer(
        AutoMaker.class.getName());
    private static DisplayManager displayManager = null;
    private ResourceBundle i18nBundle = null;
    private static Configuration configuration = null;
    private RoboxCommsManager commsManager = null;
    private AutoUpdate autoUpdater = null;
    private List<Printer> waitingForCancelFrom = new ArrayList<>();
    
    @Override
    public void start(Stage stage) throws Exception
    {
//        final Parameters params = getParameters();
//        final List<String> parameters = params.getRaw();
//
//        final String startupModel = !parameters.isEmpty() ? parameters.get(0) : "";
//        final ArrayList<File> startupModelsToLoad = new ArrayList<>();
//
//        if (parameters.isEmpty() == false)
//        {
//            File modelFile = new File(parameters.get(0));
//
//            if (modelFile != null)
//            {
//                startupModelsToLoad.add(modelFile);
//            }
//        }

//        setAppUserIDForWindows();
        stage.getIcons().addAll(new Image(getClass().getResourceAsStream(
            "/celtech/automaker/resources/images/AutoMakerIcon_256x256.png")),
                                new Image(getClass().getResourceAsStream(
                                        "/celtech/automaker/resources/images/AutoMakerIcon_64x64.png")),
                                new Image(getClass().getResourceAsStream(
                                        "/celtech/automaker/resources/images/AutoMakerIcon_32x32.png")));
        
        String installDir = ApplicationConfiguration.getApplicationInstallDirectory(AutoMaker.class);
        Lookup.initialise();
        commsManager = RoboxCommsManager.getInstance(ApplicationConfiguration.getBinariesDirectory());
        
        try
        {
            configuration = Configuration.getInstance();
        } catch (ConfigNotLoadedException ex)
        {
            steno.error("Couldn't load application configuration");
        }
        
        displayManager = DisplayManager.getInstance();
        i18nBundle = DisplayManager.getLanguageBundle();
        
        checkMachineTypeRecognised(i18nBundle);
        
        String applicationName = i18nBundle.getString("application.title");
        displayManager.configureDisplayManager(stage, applicationName);
        
        stage.setOnCloseRequest((WindowEvent event) ->
        {
            boolean transferringDataToPrinter = false;
            
            for (Printer printer : Lookup.getConnectedPrinters())
            {
                transferringDataToPrinter = transferringDataToPrinter | printer.printerStatusProperty().get().equals(PrinterStatus.SENDING_TO_PRINTER);
            }
            
            if (transferringDataToPrinter)
            {
                boolean shutDownAnyway = Lookup.getSystemNotificationHandler().showJobsTransferringShutdownDialog();
                
                if (shutDownAnyway)
                {
                    for (Printer printer : Lookup.getConnectedPrinters())
                    {
                        waitingForCancelFrom.add(printer);
                        
                        try
                        {
                            printer.cancel((TaskResponse taskResponse) ->
                            {
                                waitingForCancelFrom.remove(printer);
                            });
                        } catch (PrinterException ex)
                        {
                            steno.error("Error cancelling print on printer " + printer.getPrinterIdentity().printerFriendlyNameProperty().get() + " - " + ex.getMessage());
                        }
                    }
                } else
                {
                    event.consume();
                }
            }
        });
        
        final AutoUpdateCompletionListener completeListener = this;
        
        stage.setOnShown((WindowEvent event) ->
        {
            autoUpdater = new AutoUpdate(ApplicationConfiguration.getApplicationShortName(),
                                         ApplicationConfiguration.getDownloadModifier(
                                             ApplicationConfiguration.getApplicationName()),
                                         completeListener);
            autoUpdater.start();

//            displayManager.loadExternalModels(startupModelsToLoad, true, false);
        });
        
        displayManager = DisplayManager.getInstance();
        i18nBundle = DisplayManager.getLanguageBundle();
        
        VBox statusSupplementaryPage = null;
        
        try
        {
            URL mainPageURL = getClass().getResource(
                "/celtech/automaker/resources/fxml/SupplementaryStatusPage.fxml");
            FXMLLoader configurationSupplementaryStatusPageLoader = new FXMLLoader(mainPageURL,
                                                                                   i18nBundle);
            statusSupplementaryPage = (VBox) configurationSupplementaryStatusPageLoader.load();
        } catch (IOException ex)
        {
            steno.error("Failed to load supplementary status page:" + ex.getMessage());
            System.err.println(ex);
        }
        
        VBox statusSlideOutHandle = displayManager.getSidePanelSlideOutHandle(ApplicationMode.STATUS);
        
        if (statusSlideOutHandle != null)
        {
            statusSlideOutHandle.getChildren().add(0, statusSupplementaryPage);
            VBox.setVgrow(statusSupplementaryPage, Priority.ALWAYS);
        }
        
        stage.show();
    }
    
    @Override
    public void autoUpdateComplete(boolean requiresShutdown
    )
    {
        if (requiresShutdown)
        {
            Platform.exit();
        } else
        {
            check3DSupported(i18nBundle);
            commsManager.start();
        }
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application. main() serves only as fallback in case the application can not be launched through deployment artifacts, e.g., in IDEs
     * with limited FX support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        launch(args);
    }
    
    @Override
    public void stop() throws Exception
    {
        int timeoutStrikes = 3;
        while (waitingForCancelFrom.size() > 0 && timeoutStrikes > 0)
        {
            Thread.sleep(1000);
            timeoutStrikes--;
        }
        
        commsManager.shutdown();
        autoUpdater.shutdown();
        displayManager.shutdown();
        ApplicationConfiguration.writeApplicationMemory();
        
        TaskController taskController = TaskController.getInstance();
        
        if (taskController.getNumberOfManagedTasks() > 0)
        {
            Thread.sleep(5000);
            taskController.shutdownAllManagedTasks();
        }
    }

//    private void setAppUserIDForWindows()
//    {
//        if (getMachineType() == MachineType.WINDOWS)
//        {
//            setCurrentProcessExplicitAppUserModelID("CelTech.AutoMaker");
//        }
//    }
//
//    public static void setCurrentProcessExplicitAppUserModelID(final String appID)
//    {
//        if (SetCurrentProcessExplicitAppUserModelID(new WString(appID)).longValue() != 0)
//        {
//            throw new RuntimeException(
//                "unable to set current process explicit AppUserModelID to: " + appID);
//        }
//    }
//
//    private static native NativeLong SetCurrentProcessExplicitAppUserModelID(WString appID);
//
//    static
//    {
//        if (getMachineType() == MachineType.WINDOWS)
//        {
//            Native.register("shell32");
//        }
//    }
}
