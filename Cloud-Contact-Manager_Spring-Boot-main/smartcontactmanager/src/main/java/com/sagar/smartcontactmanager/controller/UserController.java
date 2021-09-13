package com.sagar.smartcontactmanager.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


import com.sagar.smartcontactmanager.dao.ContactRepository;
import com.sagar.smartcontactmanager.dao.UserRepository;
import com.sagar.smartcontactmanager.entities.Contact;
import com.sagar.smartcontactmanager.entities.FileUploadResponse;
import com.sagar.smartcontactmanager.entities.User;
import com.sagar.smartcontactmanager.helper.Message;


import com.sagar.smartcontactmanager.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


@Controller
@RequestMapping("/user")
public class UserController {



    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRepository contactRepository;



    //Adding common data to response, making method to add all users
    @ModelAttribute //now this works for all i.e for /index and /add-contact also
    public void addCommonData(Model model, Principal principal){ //addCommanData will work for both index as well as add-contact
        
        //to get username we need object of dao i.e UserRepository.
        String userName = principal.getName();
		System.out.println("USERNAME " + userName);

        // get the user using username (Email)
        User user = userRepository.getUserByUserName(userName);
        System.out.println("USER " +user);

        //sending above user to user_dashboard
        model.addAttribute("user", user);
    }
    
    // dashboard Home
    @RequestMapping("/index")
    public String dashboard(Model model, Principal principal) {

        model.addAttribute("title", "User Dashboard");
        return "normal/user_dashboard";
    }


    //open add form handler
    @GetMapping("/add-contact")
    public String openAddContactForm(Model model){
        
        model.addAttribute("title", "Add Contact");
        model.addAttribute("contact", "new Contact()"); //adding new blank object contact
        return "normal/add_contact_form";
    }

    //adding add contact form
    @PostMapping("/process-contact")
    public String processContact(
            @ModelAttribute Contact contact, 
            @RequestParam("profileImage") 
            MultipartFile file, 
            Principal principal,
            HttpSession session) {

        try {
            String name = principal.getName(); //we will get name of only logged in user.
            User user = this.userRepository.getUserByUserName(name);

            //processing and uploading file.
            if(file.isEmpty()){
                //for developers only..
                System.out.println("Image File is empty...");
                contact.setImage("contact.png"); //setting default contact image from img
            } 
            else {
                //upload file to folder and update name of contact.
                contact.setImage(file.getOriginalFilename());

                //now we require path to save file to which folder
                File saveFile = new ClassPathResource("static/img").getFile();

                //getting path name and appending name to file
                Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + file.getOriginalFilename());

                Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("Image uploded successfully!");

            }

            //this is bi-direction mapping contact to user and user to contact
            user.getContacts().add(contact);
            contact.setUser(user);
            
            //now before saving image to db, we will first upload file and save it to database.

            this.userRepository.save(user); //this line is saving data into database/

            System.out.println("DATA " +contact);
            System.out.println("Contact saved to database successfully.");

            //success message on contact saving.
            session.setAttribute("message", new Message("Contact Added Successfully!", "success"));


        } catch(Exception e){
            System.out.println("ERROR!" +e.getMessage());
            e.printStackTrace();

            //error message on contact not saving.
            session.setAttribute("message", new Message("Something Went Wrong!", "danger"));
        }
        return "normal/add_contact_form";
    }

    //show view contacts handler
    @GetMapping("/show-contacts/{page}")
    public String showContacts(@PathVariable("page") Integer page, Model  m, Principal principal){

        m.addAttribute("title", "View Contacts");

        String userName = principal.getName(); //getting email
        User user = this.userRepository.getUserByUserName(userName); //this returns user

        //making object for pagination
        Pageable pageable = PageRequest.of(page, 3);

        //this is going to return Page<Contact> of contacts not List<> of contacts
        Page<Contact> contacts = this.contactRepository.findContactsByUser(user.getId(), pageable); //using user, we can get their id.

        //adding pagination, getting list of contacts
        m.addAttribute("contacts", contacts); //getting current page (already getting earlier also)
        m.addAttribute("currentPage", page); //contacts per page.
        m.addAttribute("totalPages", contacts.getTotalPages()); //returns all pages 

        return "normal/show_contacts";
    }

    //showing particular contact details
    @GetMapping("/{cId}/contact")
    public String showContactDetail(@PathVariable("cId") Integer cId, Model model, Principal principal) { //using cId we fetch contact details.
        
        System.out.println("CID " + cId);

        Optional<Contact> contactOptional = this.contactRepository.findById(cId);
        Contact contact = contactOptional.get(); //so now we will get only those contact who have pass that their id

        //solving security bug, get details of logged in user using Principal.
        String userName = principal.getName(); //getting email
        User user = this.userRepository.getUserByUserName(userName); //this returns user

        //sending this contact to view, we have to use Model
        //checking if user id is equal to contact id of only logged in user.
        if (user.getId() == contact.getUser().getId()) {
			model.addAttribute("contact", contact);
			model.addAttribute("title", contact.getName());
		}

        return "normal/contact_detail";
    }

    //Delete contact handler
    @GetMapping("/delete/{cid}")
	@Transactional
	public String deleteContact(@PathVariable("cid") Integer cId, Model model, HttpSession session,
			Principal principal)  {
		System.out.println("CID " + cId);

		Contact contact = this.contactRepository.findById(cId).get();
         // check...Assignment..image delete
		
		//delete old photo

		
		User user = this.userRepository.getUserByUserName(principal.getName());
		
		user.getContacts().remove(contact);
		
		this.userRepository.save(user);

		
		System.out.println("DELETED");
		session.setAttribute("message", new Message("Contact deleted succesfully...", "success"));

		return "redirect:/user/show-contacts/0";
	}

    //update form handler
	@PostMapping("/update-contact/{cid}")
	public String updateForm(@PathVariable("cid") Integer cid, Model m) {

		m.addAttribute("title", "Update Contact");

		Contact contact = this.contactRepository.findById(cid).get();

		m.addAttribute("contact", contact);

		return "normal/update_form";
	}

	// update contact handler
	@RequestMapping(value = "/process-update", method = RequestMethod.POST)
	public String updateHandler(@ModelAttribute Contact contact, @RequestParam("profileImage") MultipartFile file,
			Model m, HttpSession session, Principal principal) {

		try {

			// old contact details
			Contact oldcontactDetail = this.contactRepository.findById(contact.getcId()).get();

			// image..
			if (!file.isEmpty()) {

				//delete old photo

				File deleteFile = new ClassPathResource("static/img").getFile();
				File file1 = new File(deleteFile, oldcontactDetail.getImage());
				file1.delete();

				//update new photo
				File saveFile = new ClassPathResource("static/img").getFile();
				Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + file.getOriginalFilename());
				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
				contact.setImage(file.getOriginalFilename());

			} else {
				contact.setImage(oldcontactDetail.getImage());
			}

			User user = this.userRepository.getUserByUserName(principal.getName());

			contact.setUser(user);

			this.contactRepository.save(contact);

			session.setAttribute("message", new Message("Your contact is updated...", "success"));

		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("CONTACT NAME " + contact.getName());
		System.out.println("CONTACT ID " + contact.getcId());
		return "redirect:/user/" + contact.getcId() + "/contact";
	}

    //your profile handler for dashboard
    @GetMapping("/profile")
    public String yourProfile(Model model){

        model.addAttribute("title", "Profile");
        return "normal/profile";
    }

    @Autowired
    private FileStorageService fileStorageService;



    @PostMapping("single/upload")
    FileUploadResponse singleFileUplaod(@RequestParam("file") MultipartFile file) {

        String fileName = fileStorageService.storeFile(file);

        ///http://localhost:8081/download/abc.jpg
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/download/")
                .path(fileName)
                .toUriString();

        String contentType = file.getContentType();

        FileUploadResponse response = new FileUploadResponse(fileName, contentType, url);

        return response;

    }

    @GetMapping("/download/{fileName}")
    ResponseEntity<Resource> downLoadSingleFile(@PathVariable String fileName, HttpServletRequest request) {

        Resource resource = fileStorageService.downloadFile(fileName);

//        MediaType contentType = MediaType.APPLICATION_PDF;

        String mimeType;

        try {
            mimeType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException e) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        mimeType = mimeType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;fileName="+resource.getFilename())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline;fileName=" + resource.getFilename())
                .body(resource);
    }

    @PostMapping("/multiple/upload")
    List<FileUploadResponse> multipleUpload(@RequestParam("files") MultipartFile[] files) {

        if (files.length > 7) {
            throw new RuntimeException("too many files");
        }
        List<FileUploadResponse> uploadResponseList = new ArrayList<>();
        Arrays.asList(files)
                .stream()
                .forEach(file -> {
                    String fileName = fileStorageService.storeFile(file);

                    ///http://localhost:8081/download/abc.jpg
                    String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/download/")
                            .path(fileName)
                            .toUriString();

                    String contentType = file.getContentType();

                    FileUploadResponse response = new FileUploadResponse(fileName, contentType, url);
                    uploadResponseList.add(response);
                });

        return uploadResponseList;
    }

    @GetMapping("zipDownload")
    void zipDownload(@RequestParam("fileName") String[] files, HttpServletResponse response) throws IOException {
//zipoutstream - zipentry+zipentry

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            Arrays.asList(files)
                    .stream()
                    .forEach(file -> {
                        Resource resource = fileStorageService.downloadFile(file);

                        ZipEntry zipEntry = new ZipEntry(resource.getFilename());

                        try {
                            zipEntry.setSize(resource.contentLength());
                            zos.putNextEntry(zipEntry);

                            StreamUtils.copy(resource.getInputStream(), zos);

                            zos.closeEntry();
                        } catch (IOException e) {
                            System.out.println("some exception while ziping");
                        }
                    });
            zos.finish();

        }

        response.setStatus(200);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;fileName=zipfile");
    }


}

