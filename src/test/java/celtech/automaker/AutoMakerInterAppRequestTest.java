package celtech.automaker;

import celtech.roboxbase.comms.interapp.InterAppRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ianhudson
 */
public class AutoMakerInterAppRequestTest
{

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String jsonifiedClass = "{\"@class\":\"celtech.automaker.AutoMakerInterAppRequest\",\"command\":\"LOAD_MESH_INTO_LAYOUT_VIEW\",\"parameters\":[\"TestParams\"]}";

    public AutoMakerInterAppRequestTest()
    {
    }

    @BeforeClass
    public static void setUpClass()
    {
    }

    @AfterClass
    public static void tearDownClass()
    {
    }

    @Before
    public void setUp()
    {
    }

    @After
    public void tearDown()
    {
    }

    @Test
    public void serializesToJSON() throws Exception
    {
        final AutoMakerInterAppRequest packet = getTestPacket();

        String mappedValue = mapper.writeValueAsString(packet);
        assertEquals(jsonifiedClass, mappedValue);
    }

    @Test
    public void deserializesFromJSON() throws Exception
    {
        final AutoMakerInterAppRequest packet = getTestPacket();

        try
        {
            InterAppRequest packetRec = mapper.readValue(jsonifiedClass, InterAppRequest.class);
            assertEquals(packet, packetRec);
        } catch (Exception e)
        {
            System.out.println(e.getCause().getMessage());
            fail();
        }
    }

    private AutoMakerInterAppRequest getTestPacket()
    {
        AutoMakerInterAppRequest packet = new AutoMakerInterAppRequest();

        packet.setCommand(AutoMakerInterAppRequestCommands.LOAD_MESH_INTO_LAYOUT_VIEW);
        List<String> paramList = new ArrayList<>();
        paramList.add("TestParams");
        packet.setParameters(paramList);

        return packet;
    }
}
