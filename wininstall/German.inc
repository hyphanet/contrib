# SubCaption:
# Overrides the subcaptions for each of the installer pages (0=": License Agreement",
# 1=": Installation Options",2=": Installation Directory", 3=": Installing Files", 
# 4=": Completed").
SubCaption 0 "Lizenzvereinbarung"
SubCaption 1 "Installation Options"
SubCaption 2 "Installationsverzeichnis"
SubCaption 3 "Installiere Dateien"
SubCaption 4 "Beendet"

# InstallButtonText overrides the default install button text (of "Install")
InstallButtonText "Installieren"
# MiscButtonText [back button][next button][cancel button][close button] replaces the
# default text strings for the four buttons (< Back, Next >, Cancel and Close).
MiscButtonText "< Zurück" "Weiter >" "Abbrechen" "Beenden"

# FileErrorText replaces the default text that comes up when a file cannot be written to.
# This string can contain a reference to $0, which is the filename ($0 is temporarily
# changed to this value). Example: "Can not write to file $\r$\n$0$\r$\ngood luck, bitch.".
FileErrorText "Kann Datei $\r$\n$0$\r$\nnicht speichern"

# LicenseText text [button_text] specifies a string that is above the license text and will
# override the default button text of "I Agree".
LicenseText "Freenet wird unter der GNU GPL veröffentlicht" "Akzeptieren"

# SpaceTexts [req text][avail text] overrides the space required and space available text
# "Space required: " and "Space available: "
SpaceTexts "Benötigter Platz: " "Verfügbarer Platz: "

#DirText [text][subtext][browse text] specifies a string that is above the directory selection area. If this command is not specified, or no parameter is specified, then the directory page is never visible to the user (even if DirShow show is specified). If subtext is specified and not empty, it overrides the default text above the path entry box ("Select the directory to install MyApp in:"). If browse button text is specified but not empty, it overrides the default browse button text ("Browse...").

#DetailsButtonText [show details text] Replaces the default details button text of "Show details", if parameter is specified (otherwise the default is used). CompletedText [Completed text]  Replaces the default text ("Completed") that is printed at the end of the install if parameter is specified. Otherwise, the default is used.

#UninstallText text
#[subtext] Replaces the default details button text of "Show details", if parameter is specified (otherwise the default is used). Specifies the text on the first page of the uninstaller. If subtext is specified and not empty, it will replace the default secondary text on that page, "Uninstall from:".

# UninstallCaption: Replaces the default details button text of "Show details", 
# if parameter is specified (otherwise the default is used). Sets what the titlebars 
# of the uninstaller will display. By default, it is 'Name Uninstall', where Name is
#  specified with the Name  command. You can, however, override it with 
# 'MyApp uninstaller' or whatever. If you specify an empty string (""), the default 
# will be used (you can however specify " " to achieve a blank string)

#UninstallSubCaption page_number
#SubCaption overrides the subcaptions for each of the uninstaller pages (0=": Confirmation",1=": Uninstalling Files",2=": Completed"). If you specify an empty string (""), the default will be used (you can however specify " " to achieve a blank string)

# UninstallButtonText: Changes the text of the button that by default says "Uninstall"
UninstallButtonText "Deinstallieren"