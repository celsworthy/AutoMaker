package celtech.automaker;

import celtech.Lookup;
import celtech.comms.interapp.InterAppCommsConsumer;
import celtech.comms.interapp.InterAppCommsThread;
import celtech.comms.interapp.InterAppRequest;
import celtech.comms.interapp.InterAppStartupStatus;
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
import com.sun.javafx.application.LauncherImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
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
public class AutoMaker extends Application implements AutoUpdateCompletionListener, InterAppCommsConsumer
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
    private LocalWebInterface localWebInterface = null;
    private final InterAppCommsThread interAppCommsListener = new InterAppCommsThread();
    private final List<String> modelsToLoadAtStartup = new ArrayList<>();
    private String modelsToLoadAtStartup_projectName = "Import";
    private boolean modelsToLoadAtStartup_dontgroup = false;

    private final String uriScheme = "automaker:";
    private final String paramDivider = "\\?";

    @Override
    public void init() throws Exception
    {
        AutoMakerInterAppRequestCommands interAppCommand = AutoMakerInterAppRequestCommands.NONE;
        List<InterAppParameter> interAppParameters = new ArrayList<>();

        if (getParameters().getUnnamed().size() == 1)
        {
            String potentialParam = getParameters().getUnnamed().get(0);
            if (potentialParam.startsWith(uriScheme))
            {
                //We've been started through a URI scheme
                potentialParam = potentialParam.replaceAll(uriScheme, "");

                String[] paramParts = potentialParam.split(paramDivider);
                if (paramParts.length == 2)
                {
//                    steno.info("Viable param:" + potentialParam + "->" + paramParts[0] + " -------- " + paramParts[1]);
                    // Got a viable param
                    switch (paramParts[0])
                    {
                        case "loadModel":
                            String[] subParams = paramParts[1].split("&");

                            for (String subParam : subParams)
                            {
                                InterAppParameter parameter = InterAppParameter.fromParts(subParam);
                                if (parameter != null)
                                {
                                    interAppParameters.add(parameter);
                                }
                            }
                            if (interAppParameters.size() > 0)
                            {
                                interAppCommand = AutoMakerInterAppRequestCommands.LOAD_MESH_INTO_LAYOUT_VIEW;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        AutoMakerInterAppRequest interAppCommsRequest = new AutoMakerInterAppRequest();
        interAppCommsRequest.setCommand(interAppCommand);
        interAppCommsRequest.setUrlEncodedParameters(interAppParameters);

        InterAppStartupStatus startupStatus = interAppCommsListener.letUsBegin(interAppCommsRequest, this);

        if (startupStatus == InterAppStartupStatus.STARTED_OK)
        {
            String installDir = ApplicationConfiguration.getApplicationInstallDirectory(AutoMaker.class);
            Lookup.setupDefaultValues();

            ApplicationUtils.outputApplicationStartupBanner(this.getClass());

            commsManager = RoboxCommsManager.
                    getInstance(ApplicationConfiguration.getBinariesDirectory());

            try
            {
                configuration = Configuration.getInstance();
            } catch (ConfigNotLoadedException ex)
            {
                steno.error("Couldn't load application configuration");
            }

            switch (interAppCommand)
            {
                case LOAD_MESH_INTO_LAYOUT_VIEW:

                    interAppCommsRequest.getUnencodedParameters()
                            .forEach(param ->
                                    {
                                        if (param.getType() == InterAppParameterType.MODEL_NAME)
                                        {
                                            modelsToLoadAtStartup.add(param.getUnencodedParameter());
                                        } else if (param.getType() == InterAppParameterType.PROJECT_NAME)
                                        {
                                            modelsToLoadAtStartup_projectName = param.getUnencodedParameter();
                                        } else if (param.getType() == InterAppParameterType.DONT_GROUP_MODELS)
                                        {
                                            switch (param.getUnencodedParameter())
                                            {
                                                case "true":
                                                    modelsToLoadAtStartup_dontgroup = true;
                                                    break;
                                                default:
                                                    break;
                                            }
                                        }
                            });
                    break;
                default:
                    break;
            }
        }

        steno.debug("Startup status was: " + startupStatus.name());
    }

    @Override
    public void start(Stage stage) throws Exception
    {
        mainStage = new Stage();

        if (checkMachineTypeRecognised(Lookup.getLanguageBundle()))
        {
            try
            {
                displayManager = DisplayManager.getInstance();
                i18nBundle = Lookup.getLanguageBundle();

                String applicationName = i18nBundle.getString("application.title");

                displayManager.configureDisplayManager(mainStage, applicationName,
                        modelsToLoadAtStartup_projectName,
                        modelsToLoadAtStartup,
                        modelsToLoadAtStartup_dontgroup);

                attachIcons(mainStage);

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
            } catch (Throwable ex)
            {
                ex.printStackTrace();
                Platform.exit();
            }

            showMainStage();
        }
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
        }
    }

    public static void main(String[] args)
    {
        LauncherImpl.launchApplication(AutoMaker.class, AutoMakerPreloader.class, args);
    }

    @Override
    public void stop() throws Exception
    {
        interAppCommsListener.shutdown();

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

        if (commsManager != null)
        {
            commsManager.shutdown();
        }
        if (autoUpdater != null)
        {
            autoUpdater.shutdown();
        }
        if (displayManager != null)
        {
            displayManager.shutdown();
        }
        ApplicationConfiguration.writeApplicationMemory();

        if (steno.getCurrentLogLevel().isLoggable(LogLevel.DEBUG))
        {
            outputRunningThreads();
        }

        Thread.sleep(5000);
        Lookup.setShuttingDown(true);
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
     * Outputs running thread names if there are any Returns true if running
     * threads were found
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

////        mainStagePreparer.stateProperty().addListener((observableValue, oldState, newState) ->
////        {
////            if (newState == Worker.State.SUCCEEDED)
////            {
////                if (mainStagePreparer.getValue())
////                {
////                    steno.debug("show main stage");
////                    showMainStage();
////                    steno.debug("end show main stage");
////                } else
////                {
////                    steno.warning("Told not to start up");
////                }
////            }
////        });
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

            if (check3DSupported(i18nBundle))
            {
                steno.debug("3D support OK");
                WelcomeToApplicationManager.displayWelcomeIfRequired();
                steno.debug("Starting comms manager");
                commsManager.start();
            }

//            localWebInterface = new LocalWebInterface();
//            localWebInterface.start();
//            displayManager.loadExternalModels(startupModelsToLoad, true, false);
        });
        mainStage.setAlwaysOnTop(false);

        //set Stage boundaries to visible bounds of the main screen
        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        mainStage.setX(primaryScreenBounds.getMinX());
        mainStage.setY(primaryScreenBounds.getMinY());
        mainStage.setWidth(primaryScreenBounds.getWidth());
        mainStage.setHeight(primaryScreenBounds.getHeight());

        mainStage.initModality(Modality.WINDOW_MODAL);

        mainStage.show();
    }

    @Override
    public void incomingComms(InterAppRequest interAppRequest)
    {
        steno.info("Received an InterApp comms request: " + interAppRequest.toString());

        if (interAppRequest instanceof AutoMakerInterAppRequest)
        {
            AutoMakerInterAppRequest amRequest = (AutoMakerInterAppRequest) interAppRequest;
            switch (amRequest.getCommand())
            {
                case LOAD_MESH_INTO_LAYOUT_VIEW:
                    String projectName = "Import";
                    List<String> modelsToLoad = new ArrayList<>();
                    boolean dontGroupModels = false;

                    for (InterAppParameter interAppParam : amRequest.getUnencodedParameters())
                    {
                        if (interAppParam.getType() == InterAppParameterType.MODEL_NAME)
                        {
                            modelsToLoad.add(interAppParam.getUnencodedParameter());
                        } else if (interAppParam.getType() == InterAppParameterType.PROJECT_NAME)
                        {
                            projectName = interAppParam.getUnencodedParameter();
                        } else if (interAppParam.getType() == InterAppParameterType.DONT_GROUP_MODELS)
                        {
                            switch (interAppParam.getUnencodedParameter())
                            {
                                case "true":
                                    dontGroupModels = true;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    displayManager.loadModelsIntoNewProject(projectName, modelsToLoad, dontGroupModels);
                    break;
            }
        }
    }
}
