/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package celtech.automaker;

import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.DisplayManager;
import celtech.printerControl.comms.RoboxCommsManager;
import celtech.utils.AutoUpdate;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import libertysystems.configuration.ConfigNotLoadedException;
import libertysystems.configuration.Configuration;
import libertysystems.stenographer.Stenographer;
import libertysystems.stenographer.StenographerFactory;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class AutoMaker extends Application
{

    private static final Stenographer steno = StenographerFactory.getStenographer(AutoMaker.class.getName());
    private static Configuration configuration = null;
    private static DisplayManager displayManager = null;
    private RoboxCommsManager commsManager = null;
    private AutoUpdate autoUpdater = null;

    @Override
    public void start(Stage stage) throws Exception
    {
        stage.getIcons().addAll(new Image(getClass().getResourceAsStream("/celtech/automaker/resources/images/AutomakerIcon_256x256.png")),
                new Image(getClass().getResourceAsStream("/celtech/automaker/resources/images/AutomakerIcon_64x64.png")),
                new Image(getClass().getResourceAsStream("/celtech/automaker/resources/images/AutomakerIcon_32x32.png")));

        commsManager = RoboxCommsManager.getInstance(ApplicationConfiguration.getApplicationInstallDirectory(AutoMaker.class));

        try
        {
            configuration = Configuration.getInstance();
        } catch (ConfigNotLoadedException ex)
        {
            steno.error("Couldn't load application configuration");
        }

        displayManager = DisplayManager.getInstance();

        String applicationName = DisplayManager.getLanguageBundle().getString("application.title");
        displayManager.configureDisplayManager(stage, applicationName);

        commsManager.start();

        stage.show();

        autoUpdater = new AutoUpdate("AutoMaker", stage, this.getClass());
//        autoUpdater.start();
    }

    @Override
    public void stop() throws Exception
    {
        autoUpdater.shutdown();
        commsManager.shutdown();
        displayManager.shutdown();
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        launch(args);
    }
}
