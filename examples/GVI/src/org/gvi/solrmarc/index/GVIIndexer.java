/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.gvi.solrmarc.index;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.marc4j.marc.ControlField;
import org.solrmarc.index.SolrIndexer;
import org.marc4j.marc.Record;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.solrmarc.tools.Utils;

/**
 *
 * @author Thomas Kirchhoff <thomas.kirchhoff@bsz-bw.de>
 */
public class GVIIndexer extends SolrIndexer
{
    
    private String id;
    private String consortium;
    
    
    public GVIIndexer(String indexingPropsFile, String[] propertyDirs)
    {
        super(indexingPropsFile, propertyDirs);
    }

    @Override
    protected void perRecordInit(Record record)
    {
        String f001 = getFirstFieldVal(record, "001");
        consortium = guessConsortium(record, f001);
        id = "("+consortium+")"+f001;
    }
    
    protected String guessConsortium(Record record, String f001)
    {
        // guess consortium
        String field003 = getFirstFieldVal(record, "003");
        String field040a = getFirstFieldVal(record, "040a");
        if (field003 != null)
        {
            if (field003.length() > 6)
            {
                field003 = field003.substring(0, 6);
            }
            consortium = field003;
        }
        else if (field040a != null)
        {
            consortium = field040a;
        }
        else if (f001.startsWith("BV"))
        {
            consortium = "DE-604";
        }
        else
        {
            consortium = "UNDEFINED";
        }
        return consortium;
    }

    public String getConsortium(Record record)
    {
        return consortium;
    }

    public String getRecordID(Record record)
    {
        return id;
    }
    
    public Set getInstitutionID(Record record)
    {
        Set<String> result = new HashSet<String>();
        if (consortium.equals("DE-576")) // SWB
        {
            result.addAll(getFieldList(record, "924b"));
        }
        else if(consortium.equals("DE-601"))  // GBV
        {
            Set <String> ilnSet = getFieldList(record, "9802");
            for (String iln: ilnSet)
            {
                result.add("GBV_ILN_"+iln);
            }
        }
        else if(consortium.equals("DE-604"))  //BVB
        {
            result.addAll(getFieldList(record, "049a"));            
        }
        else
        {
            result.add("UNDEFINED");
        }
        return result;
    }
    
    public Set getTermID(Record record, String tagStr, String prefixStr, String keepPrefixStr)
    {
        boolean keepPrefix = Boolean.parseBoolean(keepPrefixStr);        
        Set<String> candidates = getFieldList(record, tagStr);
        Set result = new HashSet();
        for (String candidate: candidates)
        {
            if (candidate.contains(prefixStr))
            {
                result.add(keepPrefix?candidate:candidate.substring(prefixStr.length()+2));
            }
        }
        
        return result;
    }
    
    enum IllFlag
    {
        Undefined(0),
        None(1),
        Ecopy(2),
        Copy(3),
        Loan(4);
        
        private final int value;
        
        IllFlag (int value)
        {
            this.value = value;
        }
        
        public int intValue()
        {
            return this.value;
        }
        @Override public String toString()
        {
            return "IllFlag." + name();
        }        
    }
    
    /**
     * Determine ILL (Inter Library Loan) Flag
     *
     * @param record
     * @return Set ill facets
     */
    public Set getIllFlag(Record record)
    {

	// a = Fernleihe (nur Ausleihe)
        // e = Fernleihe (Kopie, elektronischer Versand an Endnutzer möglich)
        // k = Fernleihe (Nur Kopie)
        // l = Fernleihe (Kopie und Ausleihe)
        // n = Keine Fernleihe    
        Set<String> result = new HashSet<String>();
        List fields = record.getVariableFields("924");
        if (fields != null)
        {
            Iterator iterator = fields.iterator();

            while (iterator.hasNext())
            {
                DataField field = (DataField) iterator.next();
                if (null != field.getSubfield('d'))
                {
                    String data = field.getSubfield('d').getData();
                    String illCodeString = data.toUpperCase();
                    char illCode = illCodeString.length() > 0 ? illCodeString.charAt(0) : 'U';
                    switch (illCode)
                    {
                        case 'U':
                            result.add(IllFlag.Undefined.toString());
                            if (result.size() > 1) 
                                result.remove(IllFlag.Undefined.toString());
                            break;
                        case 'N':
                            result.add(IllFlag.None.toString());
                            result.remove(IllFlag.Undefined.toString());
                            if (result.size() > 1) 
                                result.remove(IllFlag.None.toString());
                            break;
                        case 'A':
                            result.add(IllFlag.Loan.toString());
                            result.remove(IllFlag.Undefined.toString());
                            result.remove(IllFlag.None.toString());                            
                            break;
                        case 'E':
                            result.add(IllFlag.Copy.toString());
                            result.add(IllFlag.Ecopy.toString());
                            result.remove(IllFlag.Undefined.toString());
                            result.remove(IllFlag.None.toString());                            
                            break;
                        case 'K':
                            result.add(IllFlag.Copy.toString());
                            result.remove(IllFlag.Undefined.toString());
                            result.remove(IllFlag.None.toString());                            
                            break;
                        case 'L':
                            result.add(IllFlag.Copy.toString());
                            result.add(IllFlag.Loan.toString());
                            result.remove(IllFlag.Undefined.toString());
                            result.remove(IllFlag.None.toString());                            
                            break;
                        default:
                            if (!(result.contains(IllFlag.Copy.toString())  || 
                                  result.contains(IllFlag.Ecopy.toString()) || 
                                  result.contains(IllFlag.Loan.toString())  )) 
                            {
                                result.add(IllFlag.Undefined.toString());
                            }
                            break;
                    }
                }
            }
        }

        if (result.isEmpty())
        {
            result.add(IllFlag.Undefined.toString());
        }

        return result;
    }

    /**
     * Determine medium of material
     * @param record
     * @return Set material medium of record 
     */
    public Set getMaterialMedium(Record record)
    {
        Set result = new LinkedHashSet();
        
        if (result.isEmpty())
        {
            result.add("UNDEFINED");
        }
        return result;
    }
    
    /**
     * Determine type of material
     * @param record
     * @return Set material type of record 
     */
    public Set getMaterialType(Record record)
    {
        Set result = new LinkedHashSet();        
        char materialTypeCode = record.getLeader().getTypeOfRecord();
        String materialType = "material_type." + materialTypeCode;
        result.add(materialType);
        return result;        
    }
    
    /**
     * Determine publication form of material
     * @param record
     * @return Set material type of record 
     */
    //public String getMaterialForm(Record record, String mapFileName )
    public String getMaterialForm(Record record)
    {
        char publicationForm = record.getLeader().marshal().charAt(7);        
        //String materialForm = "material_form."+publicationForm;
        String materialForm = ""+publicationForm;
        
        /*
        String mapName = null;
        try
        {
            mapName = loadTranslationMap(null, mapFileName);
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Map<String, String> translationMap = findMap(mapName);
        String materialFormMapped = Utils.remap(materialForm, translationMap, true);
        return materialFormMapped;
        */
        return materialForm;
    }
    
    /**
     * Determine access method of material (physical, online)
     * @param record
     * @return Set access record 
     */
    public Set<String> getMaterialAccess(Record record)
    {
        Set<String> result = new HashSet();
        //material_access.Online = 007[01]=cr OR has 856 field with indicator 40
        ControlField field007 = ((ControlField)record.getVariableField("007")); 
        if (field007 != null)
        {
            //System.out.println("DEBUG "+field007.getData());
            String accessCode = field007.getData();
            DataField data856 = (DataField) record.getVariableField("856");

            if (accessCode.length() > 1 && "cr".equals(accessCode.substring(0, 2)) ||
                (data856 != null && data856.getIndicator1()=='4' && data856.getIndicator1()=='0'))
            {
                result.add("material_access.online");
                Subfield noteField = data856.getSubfield('z');
                if (noteField != null)
                {
                    String note = noteField.getData();
                    if (note != null && note.contains("kostenfrei"))
                    {
                        result.add("material_access.online_kostenfrei");
                    }
                }
            }
        }
        
        if (result.isEmpty())
        {
            result.add("material_access.physical");            
        }
        
        return result;        
    }
    
    
    public Set<String> getSubjectTopicalTerm(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.TOPICAL_TERM);
    }

    public Set<String> getSubjectGeographicalName(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.GEOGRAPHIC_NAME);
    }

    public Set<String> getSubjectGenreForm(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.GENRE_FORM);
    }
    
    public Set<String> getSubjectPersonalName(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.PERSONAL_NAME);
    }
    
    public Set<String> getSubjectCorporateName(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.CORPORATE_NAME);
    }

    public Set<String> getSubjectMeetingName(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.MEETING_NAME);
    }
    
    public Set<String> getSubjectUniformTitle(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.UNIFORM_TITLE);
    }

    public Set<String> getSubjectChronologicalTerm(Record record, String tagStr)
    {
        return getSubject(record, tagStr, MARCSubjectCategory.CHRONOLOGICAL_TERM);
    }

    public Set<String> getSubject(Record record, String tagStr, MARCSubjectCategory subjectCategory)
    {
        Set<String> result = getFieldList(record, tagStr);
        result.addAll(getSubjectUncontrolled(record, subjectCategory));
        result.addAll(getSWDSubject(record, subjectCategory));
        return result;
    }

    public Set<String> getSubjectUncontrolled(Record record, MARCSubjectCategory subjectCategory)
    {
        Set<String> result = new LinkedHashSet<String>();
        List fields = record.getVariableFields("653");
        if (fields != null)
        {
            Iterator iterator = fields.iterator();
            while (iterator.hasNext())
            {
                DataField field = (DataField) iterator.next();
                if (field.getSubfield('a') != null)
                {
                    final int ind2 = field.getIndicator2();
                    MARCSubjectCategory marcSubjectCategory = MARCSubjectCategory.mapToMARCSubjectCategory(ind2);
                    if (marcSubjectCategory.equals(subjectCategory))
                    {
                        List<Subfield> subjects = field.getSubfields('a');
                        for (Subfield subject: subjects)
                        {
                            result.add(subject.getData());
                        }
                    }
                }
            }
        }
        return result;
    }
    
    public  Set<String> getSWDSubject(Record record, MARCSubjectCategory subjectCategory)
    {
        Set<String> result = new LinkedHashSet<String>();
        List fields = record.getVariableFields("689");
        if (fields != null)
        {
            Iterator iterator = fields.iterator();
            while (iterator.hasNext())
            {
                DataField field = (DataField) iterator.next();
                if (field.getSubfield('D') != null)
                {
                    // GND Sachbegriff
                    char gndCategory = field.getSubfield('D').getData().charAt(0);
                    MARCSubjectCategory marcSubjectCategory = GNDSubjectCategory.mapToMARCSubjectCategory(gndCategory);
                    if (marcSubjectCategory.equals(subjectCategory))
                    {
                        List<Subfield> subjects = field.getSubfields('a');
                        for (Subfield subject: subjects)
                        {
                            result.add(subject.getData());
                        }
                    }
                }
                else if (field.getSubfield('A') != null)
                {
                    // Alter SWD Sachbegriff. Muss gemappt werden!
                    char swdCategory = field.getSubfield('A').getData().charAt(0);
                    MARCSubjectCategory marcSubjectCategory = SWDSubjectCategory.mapToMARCSubjectCategory(swdCategory);
                    if (marcSubjectCategory.equals(subjectCategory))
                    {
                        List<Subfield> subjects = field.getSubfields('a');
                        for (Subfield subject: subjects)
                        {
                            result.add(subject.getData());
                        }
                        
                    }                    
                }
            }
        }        
        return result;        
    }
                

    /*
        Entitätentyp der GND: 
        $D:
            "b" - Körperschaft  
            "f" -  Kongress  
            "g" - Geografikum  
            "n" - Person (nicht individualisiert)  
            "p" - Person (individualisiert)  
            "s"  - Sachbegriff  
            "u" - Werk
        Entitätentyp der SWD:
        $A
          a     = Sachschlagwort
          b     = geographisch-ethnographisches Schlagwort
          c     = Personenschlagwort
          d     = Koerperschaftsschlagwort
          f     = Formschlagwort
          z     = Zeitschlagwort
    
    */

    public enum MARCSubjectCategory
    {
        PERSONAL_NAME, 
        CORPORATE_NAME,
        MEETING_NAME,
        UNIFORM_TITLE,
        CHRONOLOGICAL_TERM,
        TOPICAL_TERM,
        GEOGRAPHIC_NAME,
        GENRE_FORM,
        UNCONTROLLED_TERM;
        
        public static final MARCSubjectCategory mapToMARCSubjectCategory(final int indicator2From653)
        {
            final MARCSubjectCategory marcSubjectCategory;
            switch(indicator2From653)
            {
                case 0:
                    marcSubjectCategory = MARCSubjectCategory.TOPICAL_TERM;
                    break;
                case 1:
                    marcSubjectCategory = MARCSubjectCategory.PERSONAL_NAME;
                    break;
                case 2:
                    marcSubjectCategory = MARCSubjectCategory.CORPORATE_NAME;
                    break;
                case 3:
                    marcSubjectCategory = MARCSubjectCategory.MEETING_NAME;
                    break;
                case 4:
                    marcSubjectCategory = MARCSubjectCategory.CHRONOLOGICAL_TERM;
                    break;
                case 5:
                    marcSubjectCategory = MARCSubjectCategory.GEOGRAPHIC_NAME;
                    break;
                case 6:
                    marcSubjectCategory = MARCSubjectCategory.GENRE_FORM;
                    break;                    
                default:
                    marcSubjectCategory = MARCSubjectCategory.TOPICAL_TERM;
            }
            return marcSubjectCategory;            
        }
        
    }
    
    public enum GNDSubjectCategory
    {
        PERSON_NONINDIVIDUAL('n'),
        PERSON_INDIVIDUAL('p'),
        KOERPERSCHAFT('b'),
        KONGRESS('f'),
        GEOGRAFIKUM('g'),
        SACHBEGRIFF('s'),
        WERK('u');                
        private final char value;
        
        private GNDSubjectCategory(char c)
        {
            this.value = c;
        }
        
        public final char valueOf() 
        {
            return value;
        }
        
        public static final MARCSubjectCategory mapToMARCSubjectCategory(final char gndCategory)
        {
            final MARCSubjectCategory marcSubjectCategory;
            switch (gndCategory)
            {
                case 'n':
                     marcSubjectCategory = MARCSubjectCategory.PERSONAL_NAME;
                    break;
                case 'p':
                     marcSubjectCategory = MARCSubjectCategory.PERSONAL_NAME;
                    break;
                case 'b':
                     marcSubjectCategory = MARCSubjectCategory.CORPORATE_NAME;
                    break;
                case 'f':
                     marcSubjectCategory = MARCSubjectCategory.MEETING_NAME;
                    break;
                case 'g':
                     marcSubjectCategory = MARCSubjectCategory.GEOGRAPHIC_NAME;
                    break;
                case 's':
                     marcSubjectCategory = MARCSubjectCategory.TOPICAL_TERM;
                    break;
                case 'u':
                     marcSubjectCategory = MARCSubjectCategory.UNIFORM_TITLE;
                    break;
                default:
                     marcSubjectCategory = MARCSubjectCategory.TOPICAL_TERM;
            }
            return marcSubjectCategory;
        }
    }
    
    public enum SWDSubjectCategory
    {
        SACHBEGRIFF('a'),
        GEOGRAFIKUM('b'),
        PERSON('c'),
        KOERPERSCHAFT('d'),
        FORMSCHLAGWORT('f'),
        ZEITSCHLAGWORT('z');
        
        private final char value;
        
        private SWDSubjectCategory(char c)
        {
            this.value = c;
        }
        
        public final char valueOf() 
        {
            return value;
        }

        public static final MARCSubjectCategory mapToMARCSubjectCategory(final char swdCategory)
        {
            final MARCSubjectCategory marcSubjectCategory;
            switch (swdCategory)
            {
                case 'a':
                     marcSubjectCategory = MARCSubjectCategory.TOPICAL_TERM;
                    break;
                case 'b':
                     marcSubjectCategory = MARCSubjectCategory.GEOGRAPHIC_NAME;
                    break;
                case 'c':
                     marcSubjectCategory = MARCSubjectCategory.PERSONAL_NAME;
                    break;
                case 'd':
                     marcSubjectCategory = MARCSubjectCategory.CORPORATE_NAME;
                    break;
                case 'f':
                     marcSubjectCategory = MARCSubjectCategory.GENRE_FORM;
                    break;
                case 'z':
                     marcSubjectCategory = MARCSubjectCategory.CHRONOLOGICAL_TERM;
                    break;
                default:
                     marcSubjectCategory = MARCSubjectCategory.TOPICAL_TERM;
            }
            return marcSubjectCategory;
        }
    }
}