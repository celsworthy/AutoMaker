package celtech.automaker;

import celtech.roboxbase.comms.interapp.InterAppRequest;
import java.util.List;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 *
 * @author ianhudson
 */
public class AutoMakerInterAppRequest extends InterAppRequest
{

    private AutoMakerInterAppRequestCommands command;
    private List<String> parameters;

    public AutoMakerInterAppRequest()
    {
    }

    public AutoMakerInterAppRequestCommands getCommand()
    {
        return command;
    }

    public void setCommand(AutoMakerInterAppRequestCommands command)
    {
        this.command = command;
    }

    public List<String> getParameters()
    {
        return parameters;
    }

    public void setParameters(List<String> parameters)
    {
        this.parameters = parameters;
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(13, 37).
                append(command).
                append(parameters).
                toHashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof AutoMakerInterAppRequest))
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }

        AutoMakerInterAppRequest rhs = (AutoMakerInterAppRequest) obj;
        return new EqualsBuilder().
                // if deriving: appendSuper(super.equals(obj)).
                append(command, rhs.command).
                append(parameters, rhs.parameters).
                isEquals();
    }
}
