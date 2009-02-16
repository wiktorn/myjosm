// License: GPL. Copyright 2007 by Immanuel Scholz and others
//Licence: GPL
package org.openstreetmap.josm.gui;

import org.xnap.commons.i18n.I18nFactory;
import static org.openstreetmap.josm.tools.I18n.i18n;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import javax.swing.JFrame;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.tools.BugReportExceptionHandler;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Main window class application.
 *
 * @author imi
 */
public class MainApplication extends Main {
    /**
     * Allow subclassing (see JOSM.java)
     */
    public MainApplication() {}

    /**
     * Construct an main frame, ready sized and operating. Does not
     * display the frame.
     */
    public MainApplication(JFrame mainFrame, SplashScreen splash) {
        super(splash);
        mainFrame.setContentPane(contentPane);
        mainFrame.setJMenuBar(menu);
        mainFrame.setBounds(bounds);
        mainFrame.setIconImage(ImageProvider.get("logo.png").getImage());
        mainFrame.addWindowListener(new WindowAdapter(){
            @Override public void windowClosing(final WindowEvent arg0) {
                if (Main.breakBecauseUnsavedChanges())
                    return;
                Main.saveGuiGeometry();
                System.exit(0);
            }
        });
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    /**
     * Main application Startup
     */
    public static void main(final String[] argArray) {
        /* try initial language settings, may be changed later again */
        try { i18n = I18nFactory.getI18n(MainApplication.class); }
        catch (MissingResourceException ex) { Locale.setDefault(Locale.ENGLISH);}

        Thread.setDefaultUncaughtExceptionHandler(new BugReportExceptionHandler());

        // initialize the plaform hook, and
        Main.determinePlatformHook();
        // call the really early hook before we anything else
        Main.platform.preStartupHook();

        // construct argument table
        List<String> argList = Arrays.asList(argArray);
        final Map<String, Collection<String>> args = new HashMap<String, Collection<String>>();
        for (String arg : argArray) {
            if (!arg.startsWith("--"))
                arg = "--download="+arg;
            int i = arg.indexOf('=');
            String key = i == -1 ? arg.substring(2) : arg.substring(2,i);
            String value = i == -1 ? "" : arg.substring(i+1);
            Collection<String> v = args.get(key);
            if (v == null)
                v = new LinkedList<String>();
            v.add(value);
            args.put(key, v);
        }

        Main.pref.init(args.containsKey("reset-preferences"));

        String localeName = null; // The locale to use

        //Check if passed as parameter
        if(args.containsKey("language"))
            localeName = (String)(args.get("language").toArray()[0]);

        if (localeName == null)
            localeName = Main.pref.get("language", null);

        if (localeName != null) {
            Locale l;
            Locale d = Locale.getDefault();
            if(localeName.equals("he")) localeName = "iw_IL";
            int i = localeName.indexOf('_');
            if (i > 0) {
                l = new Locale(localeName.substring(0, i), localeName.substring(i + 1));
            } else {
                l = new Locale(localeName);
            }
            try {
                Locale.setDefault(l);
                i18n = I18nFactory.getI18n(MainApplication.class);
            } catch (MissingResourceException ex) {
                if(!l.getLanguage().equals("en"))
                {
                    System.out.println(tr("Unable to find translation for the locale {0}. Reverting to {1}.",
                    l.getDisplayName(), d.getDisplayName()));
                    Locale.setDefault(d);
                }
                else
                {
                    i18n = null;
                }
            }
        }

        if (argList.contains("--help") || argList.contains("-?") || argList.contains("-h")) {
            // TODO: put in a platformHook for system that have no console by default
            System.out.println(tr("Java OpenStreetMap Editor")+"\n\n"+
                    tr("usage")+":\n"+
                    "\tjava -jar josm.jar <option> <option> <option>...\n\n"+
                    tr("options")+":\n"+
                    "\t--help|-?|-h                              "+tr("Show this help")+"\n"+
                    "\t--geometry=widthxheight(+|-)x(+|-)y       "+tr("Standard unix geometry argument")+"\n"+
                    "\t[--download=]minlat,minlon,maxlat,maxlon  "+tr("Download the bounding box")+"\n"+
                    "\t[--download=]<url>                        "+tr("Download the location at the url (with lat=x&lon=y&zoom=z)")+"\n"+
                    "\t[--download=]<filename>                   "+tr("Open file (as raw gps, if .gpx)")+"\n"+
                    "\t--downloadgps=minlat,minlon,maxlat,maxlon "+tr("Download the bounding box as raw gps")+"\n"+
                    "\t--selection=<searchstring>                "+tr("Select with the given search")+"\n"+
                    "\t--no-fullscreen                           "+tr("Don't launch in fullscreen mode")+"\n"+
                    "\t--reset-preferences                       "+tr("Reset the preferences to default")+"\n\n"+
                    "\t--language=<language>                     "+tr("Set the language.")+"\n\n"+
                    tr("examples")+":\n"+
                    "\tjava -jar josm.jar track1.gpx track2.gpx london.osm\n"+
                    "\tjava -jar josm.jar http://www.openstreetmap.org/index.html?lat=43.2&lon=11.1&zoom=13\n"+
                    "\tjava -jar josm.jar london.osm --selection=http://www.ostertag.name/osm/OSM_errors_node-duplicate.xml\n"+
                    "\tjava -jar josm.jar 43.2,11.1,43.4,11.4\n\n"+
                    tr("Parameters are read in the order they are specified, so make sure you load\n"+
                    "some data before --selection")+"\n\n"+
                    tr("Instead of --download=<bbox> you may specify osm://<bbox>\n"));
            System.exit(0);
        }

        SplashScreen splash = new SplashScreen(Main.pref.getBoolean("draw.splashscreen", true));

        splash.setStatus(tr("Activating updated plugins"));
        PluginHandler.earlyCleanup();

        splash.setStatus(tr("Loading early plugins"));
        PluginHandler.loadPlugins(true);

        splash.setStatus(tr("Setting defaults"));
        preConstructorInit(args);
        splash.setStatus(tr("Creating main GUI"));
        JFrame mainFrame = new JFrame(tr("Java OpenStreetMap Editor"));
        Main.parent = mainFrame;
        final Main main = new MainApplication(mainFrame, splash);
        splash.setStatus(tr("Loading plugins"));
        PluginHandler.loadPlugins(false);
        toolbar.refreshToolbarControl();

        mainFrame.setVisible(true);
        splash.closeSplash();

        if (!args.containsKey("no-fullscreen") && !args.containsKey("geometry") && Main.pref.get("gui.geometry").length() == 0 && Toolkit.getDefaultToolkit().isFrameStateSupported(JFrame.MAXIMIZED_BOTH))
            mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        EventQueue.invokeLater(new Runnable(){
            public void run() {
                main.postConstructorProcessCmdLine(args);
            }
        });
    }

}
