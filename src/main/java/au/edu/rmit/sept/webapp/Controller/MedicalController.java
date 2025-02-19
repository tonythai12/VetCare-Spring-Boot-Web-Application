package au.edu.rmit.sept.webapp.controller;

import au.edu.rmit.sept.webapp.model.Medical;
import au.edu.rmit.sept.webapp.model.Vaccination;
import au.edu.rmit.sept.webapp.model.MedicalCondition;
import au.edu.rmit.sept.webapp.model.TreatmentPlan;
import au.edu.rmit.sept.webapp.service.MedicalService;

import au.edu.rmit.sept.webapp.service.TreatmentPlanService;
import au.edu.rmit.sept.webapp.service.VaccinationService;
import jakarta.mail.MessagingException;
import au.edu.rmit.sept.webapp.service.EmailService;
import au.edu.rmit.sept.webapp.service.MedicalConditionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class MedicalController {

    @Autowired
    private LoginController loginController; 

    @Autowired
    private MedicalService medicalService; 

    @Autowired
    private VaccinationService vaccinationService; 

    @Autowired
    private MedicalConditionService medicalConditionService;

    @Autowired
    private TreatmentPlanService treatmentPlanService;

    @Autowired
    private EmailService emailService;

    // ------------------------------------------ Getting Medical Record Data -------------------------------------------------
    @GetMapping("/medical")
public String showMedicalHistory(@RequestParam(required = false) String sessionToken,
                                 @RequestParam(required = false) String petName, 
                                 Model model) {

    // Get the email using the new method
    String email = getEmailFromSessionToken(sessionToken);
    
    if (email == null) {
        model.addAttribute("isLoggedIn", false);
        return "medical";  // Show the not logged in page
    }

    // User is logged in
    model.addAttribute("isLoggedIn", true);

    // Fetch pet names for the dropdown list
    List<Medical> pets = medicalService.getMedicalRecordsByEmail(email);
    List<String> petNames = pets.stream().map(Medical::getPetName).distinct().toList();
    model.addAttribute("petNames", petNames);

    // If a petName is provided, fetch its medical details
    if (petName != null && !petName.isEmpty()) {
        Medical medicalRecord = medicalService.getMedicalRecordByEmailAndPetName(email, petName);
        var vaccinationRecord = vaccinationService.getVaccinationRecordByEmailAndPetName(email, petName);
        var medicalConditions = medicalConditionService.getMedicalConditionsByEmailAndPetName(email, petName);
        var treatmentPlan = treatmentPlanService.getTreatmentPlansByEmailAndPetName(email, petName);

        model.addAttribute("medicalRecord", medicalRecord);
        model.addAttribute("vaccinationRecord", vaccinationRecord);
        model.addAttribute("medicalConditions", medicalConditions);
        model.addAttribute("treatmentPlan", treatmentPlan);

    } else {
        model.addAttribute("medicalRecord", null);  
        model.addAttribute("vaccinationRecord", null); 
        model.addAttribute("medicalConditions", null); 
        model.addAttribute("treatmentPlan", null);
    }

    model.addAttribute("selectedPetName", petName); // Pass selected pet name to the view

    return "medical";
}


    //------------------------------------------------- Adding/Saving Record Data -------------------------------------------------
    @GetMapping("/addReport")
    public String showAddReportForm(@RequestParam String sessionToken, Model model) {
        String email = getEmailFromSessionToken(sessionToken);
    
        if (email == null) {
            model.addAttribute("isLoggedIn", false);
            return "redirect:/login";
        }
    
        model.addAttribute("isLoggedIn", true);
    
        // Fetch pets for the logged-in user
        List<Medical> pets = medicalService.getMedicalRecordsByEmail(email);
        model.addAttribute("pets", pets);
        model.addAttribute("vaccination", new Vaccination());
        model.addAttribute("medicalCondition", new MedicalCondition());
        model.addAttribute("treatmentPlan", new TreatmentPlan());
        model.addAttribute("email", email);
    
        return "addReport";
    }

    @PostMapping("/addReport")
public String addOrUpdateReport(@ModelAttribute Medical medical, 
                                @ModelAttribute Vaccination vaccination,
                                @RequestParam String email,
                                @RequestParam String petName,
                                @RequestParam List<String> medical_condition,
                                @RequestParam List<String> treatmentName, 
                                @RequestParam List<String> treatmentDate,
                                Model model) {

    // Fetch the existing pet's details
    Medical existingPet = medicalService.getMedicalRecordByEmailAndPetName(email, petName);

    if (existingPet == null) {
        model.addAttribute("error", "Selected pet not found.");
        model.addAttribute("email", email);
        return "addReport";
    }

    try {


        // Update or save the vaccination record
        Vaccination existingVaccination = vaccinationService.getVaccinationRecordByEmailAndPetName(email, petName);
        if (existingVaccination != null) {
            existingVaccination.setDistemper(vaccination.isDistemper());
            existingVaccination.setCanineParvovirus(vaccination.isCanineParvovirus());
            existingVaccination.setBordetella(vaccination.isBordetella());
            existingVaccination.setLymeDisease(vaccination.isLymeDisease());
            existingVaccination.setRabies(vaccination.isRabies());
            // update other vaccination fields if necessary
            vaccinationService.saveVaccinationRecord(existingVaccination);
        } else {
            vaccination.setEmail(email);
            vaccination.setPetName(existingPet.getPetName());
            vaccinationService.saveVaccinationRecord(vaccination);
        }

        // Update or save medical conditions
        List<MedicalCondition> existingConditions = medicalConditionService.getMedicalConditionsByEmailAndPetName(email, petName);
        medicalConditionService.deleteMedicalConditionsByEmailAndPetName(email, petName); // Delete existing ones to replace with new
        for (String condition : medical_condition) {
            if (!condition.isEmpty()) {  
                MedicalCondition newCondition = new MedicalCondition();
                newCondition.setEmail(email);
                newCondition.setPetName(petName);
                newCondition.setCondition(condition);
                medicalConditionService.saveMedicalCondition(newCondition);
            }
        }

        // Update or save treatment plans
        treatmentPlanService.deleteTreatmentPlansByEmailAndPetName(email, petName); // Delete existing to replace with new
        for (int i = 0; i < treatmentName.size(); i++) {
            if (!treatmentName.get(i).isEmpty() && !treatmentDate.get(i).isEmpty()) {
                TreatmentPlan treatmentPlan = new TreatmentPlan();
                treatmentPlan.setEmail(email);
                treatmentPlan.setPetName(petName);
                treatmentPlan.setTreatmentName(treatmentName.get(i));
                treatmentPlan.setTreatmentDate(LocalDate.parse(treatmentDate.get(i))); 
                treatmentPlanService.saveTreatmentPlan(treatmentPlan);
            }
        }

        return "redirect:/medical"; // Redirect to the medical records page after submission

    } catch (Exception e) {
        model.addAttribute("error", e.getMessage());
        model.addAttribute("email", email);
        return "addReport"; // Return to the form with an error message
    }
}



    //------------------------------------------------- Sharing Record Data -------------------------------------------------
    @PostMapping("/shareReport")
    public String shareReport(
            @RequestParam("email") String email,
            @RequestPart("pdfFile") MultipartFile pdfFile,
            Model model) throws MessagingException {

        try {
            // Convert the MultipartFile to byte array
            byte[] pdfBytes = pdfFile.getBytes();
            // Send the email with attachment using EmailService
            emailService.sendEmailWithAttachment(email, pdfBytes);
            model.addAttribute("successMessage", "Medical report shared successfully!");
        } catch (IOException e) {
            model.addAttribute("errorMessage", "Error while sharing the medical report: " + e.getMessage());
            e.printStackTrace();
        }

        return "redirect:/medical"; // Redirect back to medical records page
    }



    private String getEmailFromSessionToken(String sessionToken) {
        System.out.println("Session Token Received: " + sessionToken);
        Map<String, String> sessionTokens = loginController.getSessionTokens();
        String email = sessionTokens.get(sessionToken);
        if (email == null) {
            System.out.println("No email found for session token: " + sessionToken);
        } else {
            System.out.println("Email Retrieved: " + email);
        }
        return email;
    }

}