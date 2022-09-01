package ws.nmathe.saber.commands.general;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.commands.CommandInfo;
import ws.nmathe.saber.core.command.CommandParser.EventCompat;
import ws.nmathe.saber.core.google.GoogleAuth;
import ws.nmathe.saber.utils.Logging;
import ws.nmathe.saber.utils.MessageUtilities;

import java.io.IOException;

/**
 * used to set the google authorization token for a user
 */
public class OAuthCommand implements Command
{
    @Override
    public String name()
    {
        return "oauth";
    }

    @Override
    public CommandInfo info(String prefix)
    {
        String head = prefix + this.name();
        String usage = "``" + head + "`` - authorize access to Google Calendar";
        CommandInfo info = new CommandInfo(usage, CommandInfo.CommandType.GOOGLE);

        String cat1 = "- Usage\n" + head + " [<token>]";
        String cont1 = "This command is used to authorizes access to the private Google Calendar calendars and events associated with a Google User account." +
                "\n\n" +
                "Authorizing access will allow me to both access (read) your private events" +
                " and calendars and modify those events and calendars.\n" +
                "This will allow you to export events created on discord to a Google Calendar.\n" +
                "Using the command without arguments will indicate if there is currently an authorization token linked " +
                "to your discord ID, as well as provides an authorization link.\n" +
                "Authorizing through the provided link will provide you with an authorization token." +
                "\n\n" +
                "To link that authorization token with your Discord user ID, add the authorization token as the first argument to this command.\n" +
                "Only one Google Account may be authorized per discord user, providing another authorization token with this command will overwrite the old token.";
        info.addUsageCategory(cat1, cont1);

        info.addUsageExample(head + " 4/6KcRgz5XUrkfDD8WPMQx7G6RFosmkfF4dcDooKx6t98");

        return info;
    }

    @Override
    public String verify(String prefix, String[] args, EventCompat event)
    {
        if(args.length > 1) return "That is too many arguments!\n" +
                "Use ``" + prefix + this.name() + " [token]`` to link your Discord ID with an authorization token.";
        return "";
    }

    @Override
    public void action(String prefix, String[] args, EventCompat event)
    {
        String message = "";
        if(args.length == 0)
        {
            String googleAuth;
            Credential credential;
            try
            {
                credential = GoogleAuth.authorize(event.getAuthor().getId());
                googleAuth = GoogleAuth.newAuthorizationUrl();
            }
            catch (IOException e)
            {
                return;
            }

            // send information to the user
            if(credential == null)
            {
                message = "There is no Google account associated with your Discord User ID.\n" +
                        "Authorize with following link to receive an authorization token.\n" +
                        "You can then use that token with the ``"+this.name()+"`` command to link your Google account.\n" + googleAuth;
            }
            else
            {
                message = "Your Discord User ID is currently associated with a Google Account access token.\n" +
                        "You can authorize access to a different account's calendars by obtaining a different access token from the following link.\n" +
                        "However, only one Google Account at a time may be linked with your Discord User ID.\n" + googleAuth;
            }
        }
        else
        {
            if(args[0].equalsIgnoreCase("off"))
            {
                try
                {
                    GoogleAuth.unauthorize(event.getAuthor().getId());
                    message = "Your associated authorization token as been removed.";
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                try
                {
                    if(GoogleAuth.authorize(args[0], event.getAuthor().getId()) == null)
                    {
                        message = "I failed to authorize your account!";
                    }
                    else
                    {
                        message = "I've been successfully authorized by your Google Account!\n" +
                                "Your Google authentication token has been linked to your Discord ID.";
                    }
                }
                catch(TokenResponseException e)
                {
                    Logging.exception(this.getClass(), e);
                    message = "I failed to authorize your account! " + e.getDetails().getErrorDescription();
                }
                catch (IOException e)
                {
                    message = "I failed to authorize your account!";
                }
            }
        }

        // send the message to the user
        if(event.isFromType(ChannelType.PRIVATE))
        {
            MessageUtilities.sendPrivateMsg(message, event.getAuthor(), null);
        }
        else
        {
            MessageUtilities.sendMsg(message, event.getChannel(), null);
        }
    }
}
