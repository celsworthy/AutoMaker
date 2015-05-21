package celtech.automaker;

import celtech.Lookup;
import celtech.appManager.ApplicationMode;
import celtech.appManager.TaskController;
import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.DisplayManager;
import celtech.printerControl.comms.RoboxCommsManager;
import celtech.printerControl.model.Printer;
import celtech.printerControl.model.PrinterException;
import celtech.utils.AutoUpdate;
import celtech.utils.AutoUpdateCompletionListener;
import static celtech.utils.SystemValidation.check3DSupported;
import static celtech.utils.SystemValidation.checkMachineTypeRecognised;
import celtech.utils.application.ApplicationUtils;
import celtech.utils.tasks.TaskResponse;
import celtech.webserver.LocalWebInterface;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import libertysystems.configuration.ConfigNotLoadedException;
import libertysystems.configuration.Configuration;
import libertysystems.stenographer.LogLevel;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;
import sun.misc.ThreadGroupUtils;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class AutoMaker extends Application implements AutoUpdateCompletionListener
{

    private static final Stenographer steno;

    static
    {
        steno = StenographerFactory.getStenographer(AutoMaker.class.getName());
    }
    private static DisplayManager displayManager = null;
    private ResourceBundle i18nBundle = null;
    private static Configuration configuration = null;
    private RoboxCommsManager commsManager = null;
    private AutoUpdate autoUpdater = null;
    private List<Printer> waitingForCancelFrom = new ArrayList<>();
    private Stage mainStage;
    private Pane splashLayout;
    private double splashWidth;
    private double splashHeight;
    private LocalWebInterface localWebInterface = null;

    @Override
    public void start(Stage stage) throws Exception
    {
        String installDir = ApplicationConfiguration.getApplicationInstallDirectory(AutoMaker.class);
        Lookup.setupDefaultValues();

        ApplicationUtils.outputApplicationStartupBanner(this.getClass());
        mainStage = new Stage();

        final Task<Boolean> mainStagePreparer = new Task<Boolean>()
        {
            @Override
            protected Boolean call() throws InterruptedException
            {
                try
                {
                    attachIcons(mainStage);

                    commsManager = RoboxCommsManager.
                        getInstance(ApplicationConfiguration.getBinariesDirectory());

                    try
                    {
                        configuration = Configuration.getInstance();
                    } catch (ConfigNotLoadedException ex)
                    {
                        steno.error("Couldn't load application configuration");
                    }

                    displayManager = DisplayManager.getInstance();
                    i18nBundle = Lookup.getLanguageBundle();

                    checkMachineTypeRecognised(i18nBundle);

                    String applicationName = i18nBundle.getString("application.title");
                    displayManager.configureDisplayManager(mainStage, applicationName);

                    mainStage.setOnCloseRequest((WindowEvent event) ->
                    {
                        boolean transferringDataToPrinter = false;
                        boolean willShutDown = true;

                        for (Printer printer : Lookup.getConnectedPrinters())
                        {
                            transferringDataToPrinter = transferringDataToPrinter
                                | printer.getPrintEngine().transferGCodeToPrinterService.isRunning();
                        }

                        if (transferringDataToPrinter)
                        {
                            boolean shutDownAnyway = Lookup.getSystemNotificationHandler().
                                showJobsTransferringShutdownDialog();

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
                                        steno.error("Error cancelling print on printer " + printer.
                                            getPrinterIdentity().printerFriendlyNameProperty().get()
                                            + " - "
                                            + ex.getMessage());
                                    }
                                }
                            } else
                            {
                                event.consume();
                                willShutDown = false;
                            }
                        }

                        if (willShutDown)
                        {
                            ApplicationUtils.outputApplicationShutdownBanner();
                            Platform.exit();
                        } else
                        {
                            steno.info("Shutdown aborted - transfers to printer were in progress");
                        }
                    });

                    VBox statusSupplementaryPage = null;

                    try
                    {
                        URL mainPageURL = getClass().getResource(
                            "/celtech/automaker/resources/fxml/SupplementaryStatusPage.fxml");
                        FXMLLoader configurationSupplementaryStatusPageLoader = new FXMLLoader(
                            mainPageURL,
                            i18nBundle);
                        statusSupplementaryPage = (VBox) configurationSupplementaryStatusPageLoader.
                            load();
                    } catch (IOException ex)
                    {
                        steno.error("Failed to load supplementary status page:" + ex.getMessage());
                        System.err.println(ex);
                    }

                    VBox statusSlideOutHandle = displayManager.
                        getSidePanelSlideOutHandle(ApplicationMode.STATUS);

                    if (statusSlideOutHandle != null)
                    {
                        statusSlideOutHandle.getChildren().add(0, statusSupplementaryPage);
                        VBox.setVgrow(statusSupplementaryPage, Priority.ALWAYS);
                    }
                } catch (Exception ex)
                {
                    ex.printStackTrace();
                }
                return false;
            }

        };

        showSplash(stage, mainStagePreparer);
    }

    private void attachIcons(Stage stage)
    {
        stage.getIcons().addAll(new Image(getClass().getResourceAsStream(
            "/celtech/automaker/resources/images/AutoMakerIcon_256x256.png")),
                                new Image(getClass().getResourceAsStream(
                                        "/celtech/automaker/resources/images/AutoMakerIcon_64x64.png")),
                                new Image(getClass().getResourceAsStream(
                                        "/celtech/automaker/resources/images/AutoMakerIcon_32x32.png")));
    }

    @Override
    public void autoUpdateComplete(boolean requiresShutdown)
    {
        if (requiresShutdown)
        {
            Platform.exit();
        } else
        {
            if (check3DSupported(i18nBundle))
            {
                WelcomeToApplicationManager.displayWelcomeIfRequired();
                commsManager.start();
            }
        }
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application. main() serves only as
     * fallback in case the application can not be launched through deployment artifacts, e.g., in
     * IDEs with limited FX support. NetBeans ignores main().
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
        if (localWebInterface != null)
        {
            localWebInterface.stop();
        }

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

        if (steno.getCurrentLogLevel().isLoggable(LogLevel.DEBUG))
        {
            outputRunningThreads();
        }

        TaskController taskController = TaskController.getInstance();

        Thread.sleep(5000);
        taskController.shutdownAllManagedTasks();
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
    /**
     * Indicates whether any threads are believed to be running
     *
     * @return
     */
    private boolean areThreadsStillRunning()
    {
        ThreadGroup rootThreadGroup = ThreadGroupUtils.getRootThreadGroup();
        int numberOfThreads = rootThreadGroup.activeCount();
        return numberOfThreads > 0;
    }

    /**
     * Outputs running thread names if there are any Returns true if running threads were found
     *
     * @return
     */
    private boolean outputRunningThreads()
    {
        ThreadGroup rootThreadGroup = ThreadGroupUtils.getRootThreadGroup();
        int numberOfThreads = rootThreadGroup.activeCount();
        Thread[] threadList = new Thread[numberOfThreads];
        rootThreadGroup.enumerate(threadList, true);

        if (numberOfThreads > 0)
        {
            steno.info("There are " + numberOfThreads + " threads running:");
            for (Thread th : threadList)
            {
                steno.passthrough("---------------------------------------------------");
                steno.passthrough("THREAD DUMP:" + th.getName()
                    + " isDaemon=" + th.isDaemon()
                    + " isAlive=" + th.isAlive());
                for (StackTraceElement element : th.getStackTrace())
                {
                    steno.passthrough(">>>" + element.toString());
                }
                steno.passthrough("---------------------------------------------------");
            }
        }

        return numberOfThreads > 0;
    }

    private void showSplash(Stage splashStage, Task<Boolean> mainStagePreparer)
    {
        splashStage.setAlwaysOnTop(true);
        attachIcons(splashStage);

        Image splashImage = new Image(getClass().getResourceAsStream(
            ApplicationConfiguration.imageResourcePath
            + "Splash - AutoMaker (Drop Shadow) 600x400.png"));
        ImageView splash = new ImageView(splashImage);

        splashWidth = splashImage.getWidth();
        splashHeight = splashImage.getHeight();
        splashLayout = new AnchorPane();

        SimpleDateFormat yearFormatter = new SimpleDateFormat("YYYY");
        String yearString = yearFormatter.format(new Date());
        Text copyrightLabel = new Text("© " + yearString
            + " CEL Technology Ltd. All Rights Reserved.");
        copyrightLabel.getStyleClass().add("splashCopyright");
        AnchorPane.setBottomAnchor(copyrightLabel, 45.0);
        AnchorPane.setLeftAnchor(copyrightLabel, 50.0);

        String versionString = ApplicationConfiguration.getApplicationVersion();;
        Text versionLabel = new Text("Version " + versionString);
        versionLabel.getStyleClass().add("splashVersion");
        AnchorPane.setBottomAnchor(versionLabel, 45.0);
        AnchorPane.setRightAnchor(versionLabel, 50.0);

        splashLayout.setStyle("-fx-background-color: rgba(255, 0, 0, 0);");
        splashLayout.getChildren().addAll(splash, copyrightLabel, versionLabel);

        Scene splashScene = new Scene(splashLayout, Color.TRANSPARENT);
        splashScene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());
        splashStage.initStyle(StageStyle.TRANSPARENT);

        final Rectangle2D bounds = Screen.getPrimary().getBounds();
        splashStage.setScene(splashScene);
        splashStage.setX(bounds.getMinX() + bounds.getWidth() / 2 - splashWidth / 2);
        splashStage.setY(bounds.getMinY() + bounds.getHeight() / 2 - splashHeight / 2);

        mainStagePreparer.stateProperty().addListener((observableValue, oldState, newState) ->
        {
            if (newState == Worker.State.SUCCEEDED)
            {
                showMainStage();
                FadeTransition fadeSplash = new FadeTransition(Duration.seconds(2), splashLayout);
                fadeSplash.setFromValue(1.0);
                fadeSplash.setToValue(0.0);
                fadeSplash.setOnFinished(actionEvent ->
                {
                    splashStage.hide();
                });
                fadeSplash.play();
            }
        });

        splashStage.show();

        Thread aThread = new Thread(() ->
        {
            try
            {
                Thread.sleep(1000);
            } catch (InterruptedException ex)
            {
            }

            Lookup.getTaskExecutor().runOnGUIThread(() ->
            {
                mainStagePreparer.run();
            });
        });

        aThread.start();
    }

    private void showMainStage()
    {
        final AutoUpdateCompletionListener completeListener = this;

        mainStage.setOnShown((WindowEvent event) ->
        {
            autoUpdater = new AutoUpdate(ApplicationConfiguration.getApplicationShortName(),
                                         ApplicationConfiguration.getDownloadModifier(
                                             ApplicationConfiguration.getApplicationName()),
                                         completeListener);
            autoUpdater.start();

//            localWebInterface = new LocalWebInterface();
//            localWebInterface.start();
//            displayManager.loadExternalModels(startupModelsToLoad, true, false);
        });
        mainStage.setAlwaysOnTop(false);
//        mainStage.setMaxHeight(600);
//        mainStage.setMaxWidth(600);
        mainStage.show();
    }
}
